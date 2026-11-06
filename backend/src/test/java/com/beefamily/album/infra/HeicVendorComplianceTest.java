package com.beefamily.album.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.jar.JarFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T01 基础设施的合规 / 依赖 / 运维脚本验收（docs/07 §9.2、§11.1、§12.1 L-1..L-3）。
 *
 * <p>全部断言读真实产物文件，不依赖网络、不连数据库、不需要口令。</p>
 */
class HeicVendorComplianceTest {

    private static final String LOCKED_COMMIT = "1118f3c45a47a60cde593a6c47848b2a3a961b9a";
    private static final String PINNED_JAR_SHA256 =
            "23e476205b89570c5eba4a62551b14a3a360570f3ca2db85578db44969425ddf";

    /**
     * 定位 backend 目录（surefire 的 user.dir 通常是 backend/，向上探测兜底）。
     *
     * @return backend 目录
     */
    private static Path backendDir() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 5 && current != null; i++, current = current.getParent()) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("src/main/java"))) {
                return current;
            }
        }
        throw new IllegalStateException("找不到 backend 目录：user.dir=" + System.getProperty("user.dir"));
    }

    private static String read(Path path) throws IOException {
        assertThat(Files.isRegularFile(path)).as("%s 必须存在", path).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = HeicVendorComplianceTest.class.getResourceAsStream(name)) {
            assertThat(in).as("classpath 资源 %s 必须存在", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("COMMIT.lock 锁定 tag v26.5.1 与具体 commit，且记录可复现 SHA-256")
    void commitLockPinsUpstream() throws IOException {
        String lock = read(backendDir().resolve("lib/COMMIT.lock"));
        assertThat(lock).contains("https://github.com/openize-com/openize-heic-java");
        assertThat(lock).contains("26.5.1");
        assertThat(lock).contains("v26.5.1");
        assertThat(lock).contains(LOCKED_COMMIT);
        assertThat(lock).contains(PINNED_JAR_SHA256);
    }

    @Test
    @DisplayName("vendored jar 的 SHA-256 与 COMMIT.lock 的 pinned 值逐字一致")
    void vendoredJarMatchesPinnedHash() throws IOException, NoSuchAlgorithmException {
        Path jar = backendDir().resolve("lib/openize-heic-26.5.1.jar");
        assertThat(Files.isRegularFile(jar)).isTrue();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String actual = HexFormat.of().formatHex(digest.digest(Files.readAllBytes(jar)));
        assertThat(actual).isEqualTo(PINNED_JAR_SHA256);
    }

    @Test
    @DisplayName("vendored jar 内含解码类与许可证，且无 native 二进制 / Nokia 样本")
    void vendoredJarIsPureJava() throws IOException {
        Path jar = backendDir().resolve("lib/openize-heic-26.5.1.jar");
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            assertThat(jarFile.getJarEntry("META-INF/LICENSE.txt")).isNotNull();
            long classes = jarFile.stream().filter(e -> e.getName().endsWith(".class")).count();
            assertThat(classes).isGreaterThan(100);
            assertThat(jarFile.stream().noneMatch(e -> e.getName().endsWith(".so")
                    || e.getName().endsWith(".dll") || e.getName().endsWith(".dylib"))).isTrue();
            assertThat(jarFile.stream().noneMatch(e -> e.getName().toLowerCase().contains("nokia"))).isTrue();
        }
    }

    @ParameterizedTest(name = "许可证归档：{0}")
    @ValueSource(strings = {"Openize.txt", "IsoBmff-MIT.txt", "MetadataExtractor-Apache-2.0.txt"})
    @DisplayName("三份许可证原文归档且非空")
    void licensesArchived(String fileName) throws IOException {
        String text = read(backendDir().resolve("lib/licenses/" + fileName));
        assertThat(text.trim()).isNotEmpty();
    }

    @ParameterizedTest(name = "backend/README.md 合规声明须包含：{0}")
    @ValueSource(strings = {
            "1118f3c45a47a60cde593a6c47848b2a3a961b9a",
            "Openize License", "MIT License", "Apache License 2.0",
            "HEVC/H.265", "专利许可", "部署方", "本地 / 内部自用",
            "不对外提供 HEIC 转码服务", "Nokia", "纯 Java", "lib/licenses/"
    })
    @DisplayName("backend/README.md 六项合规声明逐项到位（L-1 / L-2 / L-3）")
    void complianceStatementIsComplete(String keyword) throws IOException {
        assertThat(read(backendDir().resolve("README.md"))).contains(keyword);
    }

    @ParameterizedTest(name = "pom.xml 依赖声明须包含：{0}")
    @ValueSource(strings = {
            "bee-vendor", "file://${project.basedir}/lib/maven-repo",
            "<groupId>openize</groupId>", "<artifactId>openize-heic</artifactId>", "26.5.1",
            "<exclusions>", "metadata-extractor", "2.19.0"
    })
    @DisplayName("pom.xml 用项目内 file:// 仓库 + 排除上游 metadata-extractor，统一锁定 2.19.0")
    void pomDeclaresVendoredDependency(String keyword) throws IOException {
        assertThat(read(backendDir().resolve("pom.xml"))).contains(keyword);
    }

    @ParameterizedTest(name = "application.yml 的 HEIC 配置须包含：{0}")
    @ValueSource(strings = {"heic:", "probe-on-startup: true", "display-max-edge: 1600", "thumb-max-edge: 480"})
    @DisplayName("application.yml 的 app.heic.* 三档配置到位（§9.5）")
    void heicConfigPresent(String keyword) throws IOException {
        assertThat(resource("/application.yml")).contains(keyword);
    }

    @ParameterizedTest(name = "运维脚本：{0}")
    @ValueSource(strings = {"backup.sh", "rollback.sh"})
    @DisplayName("backup.sh / rollback.sh 存在、非空且可执行声明齐备")
    void operationScriptsExist(String fileName) throws IOException {
        Path script = backendDir().getParent().resolve("scripts").resolve(fileName);
        String text = read(script);
        assertThat(text).startsWith("#!/usr/bin/env bash");
        assertThat(text).contains("set -euo pipefail");
        assertThat(text).doesNotContain("PASSWORD=\"root\"").doesNotContain("PASSWORD:-");
    }

    @Test
    @DisplayName("backup.sh 用 mysqldump --single-transaction 全量备份，含 routines/triggers")
    void backupUsesConsistentSnapshot() throws IOException {
        String text = read(backendDir().getParent().resolve("scripts/backup.sh"));
        assertThat(text).contains("mysqldump").contains("--single-transaction")
                .contains("--routines").contains("--triggers");
    }

    @Test
    @DisplayName("rollback.sh 默认 DRY-RUN，破坏性动作必须显式 CONFIRM=YES")
    void rollbackIsDryRunByDefault() throws IOException {
        String text = read(backendDir().getParent().resolve("scripts/rollback.sh"));
        assertThat(text).contains("CONFIRM=YES").contains("DROP DATABASE").contains("DRY-RUN");
    }

    @ParameterizedTest(name = "主源码不得出现：{0}")
    @ValueSource(strings = {"ProcessBuilder", "Runtime.getRuntime", "sips", "ffmpeg", "magick", "heif-convert"})
    @DisplayName("HEIC 链路为纯 Java：主源码无外部进程 / 系统命令调用")
    void noExternalToolInvocation(String forbidden) throws IOException {
        Path src = backendDir().resolve("src/main/java");
        try (var walk = Files.walk(src)) {
            for (Path javaFile : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(javaFile, StandardCharsets.UTF_8))
                        .as("%s 不应出现 %s", javaFile.getFileName(), forbidden)
                        .doesNotContain(forbidden);
            }
        }
    }
}
