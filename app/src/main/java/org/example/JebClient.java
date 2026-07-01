package org.example;

/*
 * JEB Copyright PNF Software, Inc.
 * 
 *     https://www.pnfsoftware.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.io.File;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.List;

import com.pnfsoftware.jeb.core.RuntimeProjectUtil;
import com.pnfsoftware.jeb.core.JebCoreService;
import com.pnfsoftware.jeb.core.units.IUnit;
import com.pnfsoftware.jeb.util.base.Env;
import com.pnfsoftware.jeb.util.io.IO;
import com.pnfsoftware.jeb.util.logging.GlobalLog;
import com.pnfsoftware.jeb.util.logging.ILogger;
import com.pnfsoftware.jeb.core.util.DecompilerHelper;
import com.pnfsoftware.jeb.core.units.code.android.IDexUnit;

/**
 * Skeleton file for a custom JEB client. Requires JEB Pro version 5.37 or above.
 * <p>
 * This simple client that takes a list of files and processes them. It can be extended to write
 * some automation code for bulk processing.
 * <p>
 * The sole dependency is {@code jeb.jar}. If you have JEB installed on your machine pointed to by
 * {@code $JEB_HOME}, this code will try to pull information (such as license key and engines
 * configuration) from that installation.
 * <p>
 * API reference: https://www.pnfsoftware.com/jeb/apidoc/
 * 
 */
public class JebClient {
    static final ILogger logger = GlobalLog.getLogger(JebClient.class);
    static {
        GlobalLog.addDestinationStream(System.out);
    }

    // provide a folder with files as the command-line argument
    public static void main(String[] args) throws Exception {
        if(args.length == 0) {
            throw new RuntimeException("Provide a directory for scanning as the first command-line argument");
        }

        var jebHome = Env.get("JEB_HOME");
        if(jebHome == null) {
            throw new RuntimeException("Set the JEB_HOME environment variable to point to your JEB folder");
        }
        logger.info("JEB_HOME: %s", jebHome);

        // instantiate the JEB core service
        var jeb = JebCoreService.getInstance(new File(jebHome));

        // create an engines context (a container of JEB projects)
        var engctx = jeb.createEnginesContext();

        // scan some files
        var files = IO.listFiles(args[0]);
        logger.info("%d files found", files.size());
        int i = 0;
        for(var file: files) {
            i++;
            logger.info("Processing file %d/%d : %s ...", i, files.size(), file.getName());

            // create or load a project (artifact container)
            var prj = engctx.createProject("ProjectTest" + i);

            // process the artifact, get units
            var art = prj.processArtifact(file);

            // proceed with the units
            var units = art.getUnits();

            // work with the units
            for(IUnit unit: units) {
                logger.info("Unit: %s", unit);
                var decompiler = DecompilerHelper.getDecompiler(unit);
                if (decompiler == null) {
                    logger.info("decompiler is null for %s", unit);
                    continue;
                }
                var exporter = decompiler.getExporter();
                if (exporter == null) {
                    logger.info("exporter is null for %s", decompiler);
                    continue;
                }
                logger.info("exporter output folder: ", exporter.getOutputFolder());
                //
                // ... do some work
                //
            }

            engctx.unloadProject(prj.getKey());
        }

        // close our engines context
        jeb.closeEnginesContext(engctx);

        // close JEB
        jeb.close();
    }

    public static void decompileApk(String apkPath, String outPath) throws Exception {
        File apkFile = new File(apkPath);
        if (!apkFile.exists() || !apkFile.isFile()) {
            throw new RuntimeException("invalid apk file: " + apkPath);
        }

        File outDir = new File(outPath);

        if (outDir.exists()) {
            logger.error("outDir %s alread exists, please specify a new path.", outPath);
            return;
        } else {
            outDir.mkdirs();
        }

        File sourcesDir = new File(outDir, "sources");
        File resourcesDir = new File(outDir, "resources");

        if (!sourcesDir.exists()) sourcesDir.mkdirs();
        if (!resourcesDir.exists()) resourcesDir.mkdirs();

        logger.info("==================================================");
        logger.info("[*] 阶段 1: 使用 jadx 提取资源文件...");
        logger.info("==================================================");
        if (!decompileApkWithJadx(apkFile.getAbsolutePath(), resourcesDir.getAbsolutePath())) {
            logger.info("error when decompileApkWithJadx");
            return;
        }

        // logger.info("==================================================");
        // logger.info("[*] 阶段 1: 使用 Apktool 提取资源文件...");
        // logger.info("==================================================");
        // boolean apktoolSuccess = extractResourcesWithApktool(
        //     apkFile.getAbsolutePath(), resourcesDir.getAbsolutePath());
        // if (!apktoolSuccess) {
        //     logger.error("[-] Apktool 提取资源失败，请检查环境配置。");
        // }

        logger.info("==================================================");
        logger.info("[*] 阶段 2: 启动 JEB 引擎反编译 Java 源码...");
        logger.info("==================================================");

        var jebHome = Env.get("JEB_HOME");
        if(jebHome == null) {
            throw new RuntimeException("Set the JEB_HOME environment variable to point to your JEB folder");
        }
        logger.info("JEB_HOME: %s", jebHome);

        var jeb = JebCoreService.getInstance(new File(jebHome));
        var engctx = jeb.createEnginesContext();

        logger.info("processing %s", apkFile.getName());
        var prj = engctx.createProject("SingleFileProject");
        prj.processArtifact(apkFile);
        List<IUnit> allUnits = RuntimeProjectUtil.findUnitsByType(prj, IUnit.class, false);

        if (allUnits == null || allUnits.isEmpty()) {
            logger.info("[-] no unit found in %s, exit", apkFile.getName());
            return;
        }

        for (IUnit unit: allUnits) {
            String unitName = unit.getName();

            if (unit instanceof IDexUnit) {
                // logger.info("skip dex unit: %s", unitName);
                // continue;

                logger.info("[+] find DEX unit: %s，start decompilation ...", unitName);

                var decompiler = DecompilerHelper.getDecompiler(unit);
                if (decompiler == null) continue;

                var exporter = decompiler.getExporter();
                if (exporter != null) {
                    exporter.setOutputFolder(sourcesDir);
                    exporter.export();
                }
            } else {
                // logger.info("[+] find resource unit: %s", unitName);
                logger.info("find unit: %s", unitName);
            }
        }

        // for(IDexUnit dexUnit: dexUnits) {
        //     logger.info("[+] 发现 DEX 单元: %s", dexUnit.getName());

        //     var decompiler = DecompilerHelper.getDecompiler(dexUnit);
        //     if (decompiler == null) {
        //         logger.info("[-] 未能获取反编译器: %s", dexUnit.getName());
        //         continue;
        //     }

        //     var exporter = decompiler.getExporter();
        //     if (exporter == null) {
        //         logger.info("[-] 未能获取 Exporter: %s", dexUnit.getName());
        //         continue;
        //     }

        //     // 配置输出目录并执行导出
        //     exporter.setOutputFolder(targetFolder);
        //     logger.info("[+] 开始反编译并导出 Java 源码至: %s", targetFolder.getAbsolutePath());
        //     logger.info("[*] 这可能需要一些时间，请耐心等待...");

        //     boolean success = exporter.export();

        //     if (success) {
        //         logger.info("[+] 导出成功: %s", dexUnit.getName());
        //     } else {
        //         logger.error("[-] 导出失败或被中止: %s", dexUnit.getName());
        //     }
        // }
    }


    private static boolean extractResourcesWithApktool(String apkPath, String outDirPath) {
        try {
            // 构建命令: apktool d -s -f <apk路径> -o <输出路径>
            // -s: 不反编译 dex 文件 (交给 JEB 处理)
            // -f: 强制覆盖已存在的目录
            ProcessBuilder pb = new ProcessBuilder("apktool", "d", "-s", "-f", apkPath, "-o", outDirPath);
            
            // 将错误流重定向到标准输出流，方便统一读取日志
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 实时读取 Apktool 的输出日志（必须读取，否则进程可能会因缓冲区满而卡死）
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("  [Apktool] " + line);
            }

            // 等待 Apktool 执行完毕
            int exitCode = process.waitFor();
            return exitCode == 0;

        } catch (Exception e) {
            logger.error("  [-] 调用 Apktool 时发生异常: %s", e.getMessage());
            return false;
        }
    }

    private static boolean decompileApkWithJadx(String apkPath, String outDirPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("jadx", "-s", apkPath, "-dr", outDirPath);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("  [jadx] " + line);
            }
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            logger.error("  [-] 调用 jadx 时发生异常: %s", e.getMessage());
            return false;
        } 
    }
}
