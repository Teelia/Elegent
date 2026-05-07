package com.datalabeling.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 查询任务-标签绑定关系
 */
public class QueryTaskLabelBinding {

    private static final String URL = "jdbc:mysql://122.152.221.87:13308/data_labeling?useSSL=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "ea6741b12e0bea64";

    public static void main(String[] args) {
        String taskIdParam = args.length > 0 ? args[0] : "";

        if (taskIdParam.isEmpty()) {
            System.out.println("请提供任务ID作为参数");
            return;
        }

        try {
            Connection conn = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            Statement stmt = conn.createStatement();

            int taskId = Integer.parseInt(taskIdParam);

            // 查询任务-标签绑定
            java.sql.ResultSet rs = stmt.executeQuery(
                "SELECT task_id, label_id, label_version " +
                "FROM analysis_task_labels " +
                "WHERE task_id = " + taskId
            );

            System.out.println("=== 任务 " + taskId + " 的标签绑定 ===");
            System.out.println();

            boolean found = false;
            while (rs.next()) {
                found = true;
                int tid = rs.getInt("task_id");
                int labelId = rs.getInt("label_id");
                String labelVersion = rs.getString("label_version");

                System.out.println("任务ID: " + tid);
                System.out.println("绑定标签ID: " + labelId);
                System.out.println("标签版本: " + labelVersion);
            }

            if (!found) {
                System.out.println("未找到任务 " + taskId + " 的标签绑定记录");
            }

            rs.close();

            // 如果找到了绑定，查询对应的标签配置
            if (found) {
                System.out.println();
                System.out.println("=== 绑定标签的详细配置 ===");
                System.out.println();

                java.sql.ResultSet labelRs = stmt.executeQuery(
                    "SELECT id, name, version, type, extractor_config " +
                    "FROM labels " +
                    "WHERE id IN (SELECT label_id FROM analysis_task_labels WHERE task_id = " + taskId + ") " +
                    "ORDER BY version DESC"
                );

                while (labelRs.next()) {
                    int id = labelRs.getInt("id");
                    String name = labelRs.getString("name");
                    String version = labelRs.getString("version");
                    String type = labelRs.getString("type");
                    String extractorConfig = labelRs.getString("extractor_config");

                    System.out.println("标签ID: " + id);
                    System.out.println("标签名称: " + name);
                    System.out.println("标签版本: " + version);
                    System.out.println("标签类型: " + type);
                    System.out.println("extractor_config: " + extractorConfig);

                    // 解析extractorConfig
                    if (extractorConfig != null && !extractorConfig.isEmpty()) {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            com.fasterxml.jackson.databind.JsonNode config = mapper.readTree(extractorConfig);
                            System.out.println("  extractorType: " + config.path("extractorType").asText());

                            if (config.has("options")) {
                                com.fasterxml.jackson.databind.JsonNode options = config.path("options");
                                System.out.println("  include18Digit: " + options.path("include18Digit").asBoolean());
                                System.out.println("  include15Digit: " + options.path("include15Digit").asBoolean());
                                System.out.println("  includeLoose: " + options.path("includeLoose").asBoolean());
                            }
                        } catch (Exception e) {
                            System.out.println("配置解析失败: " + e.getMessage());
                        }
                    }
                    System.out.println("---");
                }
                labelRs.close();
            }

            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.err.println("查询失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
