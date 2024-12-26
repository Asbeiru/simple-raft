package helper;

import java.io.File;
import java.io.IOException;

public class DirectoryStructureCreator {

    public static void main(String[] args) {
        // 定义项目的根目录
        String baseDir = "docs";

        // 创建根目录
        createDirectory(baseDir);

        // 定义章节及其内容
        String[][] chapters = {
            {"Chapter_01", "01_CAP_定理.md", "02_共识算法.md", "Summary.md"},
            {"Chapter_02", "01_集群分析.md", "02_日志状态机模型.md", "03_Quorum机制.md", "04_选举机制.md", "05_日志复制.md", "06_细节问题.md", "Summary.md"},
            {"Chapter_03", "01_设计目标.md", "02_设计实现顺序.md", "03_参考实现.md", "04_状态数据分析.md", "05_静态数据分析.md", "06_成员映射表.md", "07_组件分析.md", "08_解耦组件.md", "09_线程模型.md", "10_项目准备.md", "Summary.md"},
            {"Chapter_04", "01_角色建模.md", "02_定时器组件.md", "03_消息建模.md", "04_组件工具.md", "05_一致性组件.md", "06_测试.md", "Summary.md"},
            {"Chapter_05", "01_日志实现要求.md", "02_日志实现分析.md", "03_日志条目序列.md", "04_日志实现.md", "05_选举对接.md", "06_测试.md", "Summary.md"},
            {"Chapter_06", "01_通信接口分析.md", "02_序列化与反序列化.md", "03_通信实现分析.md", "04_通信组件实现.md", "05_测试.md", "Summary.md"},
            {"Chapter_07", "01_服务设计.md", "02_服务实现.md", "03_Node组装.md", "04_测试.md", "Summary.md"},
            {"Chapter_08", "01_客户端设计.md", "02_客户端启动.md", "03_单机模式.md", "04_集群模式.md", "Summary.md"},
            {"Chapter_09", "01_日志快照分析.md", "02_日志快照实现.md", "03_测试.md", "Summary.md"},
            {"Chapter_10", "01_成员安全变更.md", "02_细节问题.md", "03_组件修改.md", "04_日志组件修改.md", "05_增加节点.md", "06_移除节点.md", "07_测试.md", "Summary.md"},
            {"Chapter_11", "01_PreVote.md", "02_ReadIndex.md", "03_其他优化.md", "Summary.md"},
        };

        // 创建章节目录和文件
        for (String[] chapter : chapters) {
            String chapterName = chapter[0];
            createDirectory(baseDir + File.separator + chapterName);
            for (int i = 1; i < chapter.length; i++) { // 从1开始，因为0是章节名
                createFile(baseDir + File.separator + chapterName + File.separator + chapter[i]);
            }
        }

        // 创建参考文献文件
        createFile(baseDir + File.separator + "References.md");
        
        System.out.println("目录结构创建完成！");
    }

    private static void createDirectory(String path) {
        File directory = new File(path);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    private static void createFile(String path) {
        File file = new File(path);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                System.err.println("创建文件失败: " + path);
                e.printStackTrace();
            }
        }
    }
}