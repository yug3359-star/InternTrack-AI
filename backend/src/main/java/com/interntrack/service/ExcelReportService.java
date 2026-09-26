package com.interntrack.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class ExcelReportService {

    public byte[] generateInternshipReport(List<Map<String, Object>> students, String departmentFilter) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Internship Register");

            // 1. Create Styles
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle subTitleStyle = workbook.createCellStyle();
            Font subTitleFont = workbook.createFont();
            subTitleFont.setBold(true);
            subTitleFont.setFontHeightInPoints((short) 14);
            subTitleStyle.setFont(subTitleFont);
            subTitleStyle.setAlignment(HorizontalAlignment.CENTER);
            subTitleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            // 2. Create Header Rows (Merged)
            int totalCols = 20;
            
            // ROW 1
            Row row1 = sheet.createRow(0);
            row1.setHeightInPoints(25);
            Cell cell1 = row1.createCell(0);
            cell1.setCellValue("G H RAISONI COLLEGE OF ENGINEERING, NAGPUR");
            cell1.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, totalCols - 1));

            // ROW 2
            Row row2 = sheet.createRow(1);
            row2.setHeightInPoints(20);
            Cell cell2 = row2.createCell(0);
            String deptName = (departmentFilter != null && !departmentFilter.isEmpty()) ? departmentFilter.toUpperCase() : "COMPUTER SCIENCE";
            cell2.setCellValue(deptName + " DEPARTMENT");
            cell2.setCellStyle(subTitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, totalCols - 1));

            // ROW 3
            Row row3 = sheet.createRow(2);
            row3.setHeightInPoints(20);
            Cell cell3 = row3.createCell(0);
            int currentYear = LocalDate.now().getYear();
            cell3.setCellValue("SEMESTER INTERNSHIP " + currentYear + "-" + (currentYear + 1));
            cell3.setCellStyle(subTitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, totalCols - 1));

            // 3. Create Table Headers (ROW 4)
            Row row4 = sheet.createRow(3);
            String[] headers = {
                "S.No.", "Name of Student", "Registration No.", "Roll No", "Section", 
                "Department", "Sem.", "Student Mobile No.", "College Email ID", 
                "Mode of Internship", "Company Name", "City", "Stipend", 
                "Start Date", "End Date", "Total Days", "Attendance (%)",
                "Reference Photo", "Offer Letter", "Approval Letter"
            };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = row4.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 4. Map Data (ROW 5 onward)
            int rowNum = 4;
            int sno = 1;
            for (Map<String, Object> record : students) {
                Row row = sheet.createRow(rowNum++);
                
                String[] values = {
                    String.valueOf(sno++),
                    getString(record, "fullName"),
                    getString(record, "registrationNumber"),
                    getString(record, "rollNo"),
                    getString(record, "section"),
                    getString(record, "branch"),
                    getString(record, "semester"),
                    getString(record, "mobileNumber"),
                    getString(record, "collegeEmail"),
                    getString(record, "modeOfInternship"),
                    getString(record, "companyName"),
                    getString(record, "city"),
                    getString(record, "internshipStipend"),
                    formatDate(getString(record, "joiningDate")),
                    formatDate(getString(record, "completionDate")),
                    String.valueOf(record.getOrDefault("totalDays", "0")),
                    getString(record, "attendancePercentage") + "%",
                    getString(record, "referencePhotoUrl"),
                    getString(record, "offerLetterUrl"),
                    getString(record, "approvalLetterUrl")
                };

                for (int i = 0; i < values.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(values[i]);
                    cell.setCellStyle(dataStyle);
                }
            }

            // 5. Auto-size columns
            for (int i = 0; i < totalCols; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to byte array
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return "N/A";
        String s = String.valueOf(val).trim();
        return s.isEmpty() ? "N/A" : s;
    }

    private String formatDate(String isoDate) {
        if ("N/A".equals(isoDate) || isoDate == null) return "N/A";
        try {
            LocalDate date = LocalDate.parse(isoDate);
            return String.format("%02d/%02d/%02d", date.getDayOfMonth(), date.getMonthValue(), date.getYear() % 100);
        } catch (Exception e) {
            return isoDate;
        }
    }
}
