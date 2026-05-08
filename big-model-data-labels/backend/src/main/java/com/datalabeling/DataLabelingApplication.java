package com.datalabeling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 芜湖市公安局警情分析智能体 - 主应用启动类
 *
 * @author DataLabeling Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
public class DataLabelingApplication {

    public static void main(String[] args) {
        // 设置 Java AWT headless 模式，解决 Linux 服务器下 Excel 导出问题
        // 必须在 Spring 启动之前设置
        System.setProperty("java.awt.headless", "true");

        SpringApplication.run(DataLabelingApplication.class, args);
        System.out.println("\n" +
            "========================================\n" +
            "  芜湖市公安局警情分析智能体启动成功！\n" +
            "  访问地址: http://localhost:8080/api\n" +
            "  Druid监控: http://localhost:8080/api/druid\n" +
            "========================================\n");
    }
}
