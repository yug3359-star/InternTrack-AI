package com.interntrack.controller;

import com.interntrack.security.RequireRole;
import com.interntrack.service.ExcelDataService;
import com.interntrack.service.ExcelReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hod")
public class ExcelReportController {

    private final ExcelDataService excelDataService;
    private final ExcelReportService excelReportService;

    @Autowired
    public ExcelReportController(ExcelDataService excelDataService, ExcelReportService excelReportService) {
        this.excelDataService = excelDataService;
        this.excelReportService = excelReportService;
    }

    @GetMapping("/export-excel")
    @RequireRole("hod")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String status) {
        try {
            List<Map<String, Object>> data = excelDataService.getExcelData(branch, status);
            byte[] excelFile = excelReportService.generateInternshipReport(data, branch);

            String dept = (branch != null && !branch.isEmpty()) ? branch.replaceAll("\\s+", "") : "AllDepartments";
            String filename = dept + "_Internship_Report_" + LocalDate.now() + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(excelFile.length)
                    .body(excelFile);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
