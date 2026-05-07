package com.datalabeling.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * 临时测试类：读取Excel文件内容
 */
public class ExcelReaderTest {

    public static void main(String[] args) {
        String filePath = "D:/01_代码库/大模型自动化数据标签/测试数据/analysis-task-58-results-with-reasoning.xlsx";

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            System.out.println("========================================");
            System.out.println("Excel文件内容分析");
            System.out.println("========================================");
            System.out.println("工作表名称: " + sheet.getSheetName());
            System.out.println("总行数: " + (sheet.getPhysicalNumberOfRows()));
            System.out.println();

            // 读取表头
            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                System.out.println("表头列数: " + headerRow.getPhysicalNumberOfCells());
                System.out.print("表头: ");
                for (Cell cell : headerRow) {
                    System.out.print("[" + cell.getStringCellValue() + "] ");
                }
                System.out.println();
                System.out.println();
            }

            // 读取所有数据
            System.out.println("========================================");
            System.out.println("所有数据详情:");
            System.out.println("========================================");

            int dataRows = sheet.getPhysicalNumberOfRows();
            for (int i = 1; i < dataRows; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                System.out.println("--- 第 " + i + " 行 ---");
                int cellCount = row.getPhysicalNumberOfCells();
                for (int j = 0; j < cellCount; j++) {
                    Cell cell = row.getCell(j);
                    String value = ExcelUtil.getCellValueAsString(cell);
                    if (value.length() > 100) {
                        value = value.substring(0, 100) + "...";
                    }
                    System.out.println("  列" + j + ": " + value);
                }
                System.out.println();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
