package com.generator;

import net.sf.jasperreports.engine.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class JasperReportToPDF {

    public static void main(String[] args) {
        String mode = null;
        String reportsBaseDirectory = null;
        String reportsToBuild = null;
        String reportPdfOutputName = null;
        HashMap<String, Object> params = new HashMap<>();

        // Configurações do banco de dados PostgreSQL
        String user = null;
        String password = null;
        String host = null;
        String database = null;

        if (args.length <= 1 || (args.length % 2) != 0) {
            throw new IllegalArgumentException("Not enough arguments.");
        }

        boolean argsParsed = false;
        int index = 0;
        while (!argsParsed) {
            String parameter = args[index];
            switch (parameter) {
                case "--base-dir":
                    reportsBaseDirectory = args[++index];
                    break;
                case "--parameters":
                    String parameterValue = args[++index].trim();
                    if (parameterValue.isEmpty()) {
                        break;
                    }
                    String[] rawParameters = parameterValue.split(":");
                    for (String rawParameter : rawParameters) {
                        String[] nameValue = rawParameter.split("=");
                        Pattern notIntPattern = Pattern.compile("[\\D]+");
                        Pattern doublePattern = Pattern.compile("^(?!\\d\\.\\d$).*$");
                        if (!notIntPattern.matcher(rawParameter).matches()) {
                            params.put(nameValue[0], Integer.parseInt(nameValue[1]));
                        } else if (!doublePattern.matcher(rawParameter).matches()) {
                            params.put(nameValue[0], Double.parseDouble(nameValue[1]));
                        } else {
                            params.put(nameValue[0], nameValue[1]);
                        }
                    }
                    break;
                case "--mode":
                    mode = args[++index];
                    break;
                case "--generate-from-file":
                    reportsToBuild = args[++index];
                    break;
                case "--pdf-output-name":
                    reportPdfOutputName = args[++index];
                    break;
                case "--db-username":
                    user = args[++index];
                    break;
                case "--db-password":
                    password = args[++index];
                    break;
                case "--db-host":
                    host = args[++index];
                    break;
                case "--db-database":
                    database = args[++index];
                    break;
                default:
                    throw new IllegalArgumentException("Invalid argumentsaaaaaaaa");

            }
            index++;
            if (index >= args.length) {
                argsParsed = true;
            }
        }
        if (reportsBaseDirectory == null || mode == null || user == null || password == null || host == null || database == null) {
            throw new IllegalArgumentException("base directory is required.");
        }
        if (reportPdfOutputName == null) {
            reportPdfOutputName = reportsToBuild;
        }

        String url = "jdbc:postgresql://" + host + ":5432/" + database;


        // Caminho da pasta de reports
        String compiledReportsDirectory = reportsBaseDirectory + "compiled/";
        String reportPDFDirectory = reportsBaseDirectory + "pdf/";
        String reportImagesDirectory = reportsBaseDirectory + "images/";
        String subReportDirectory = reportsBaseDirectory + "subreports/";
        String bookReportDirectory = reportsBaseDirectory + "book/";

        try {
            Files.createDirectories(Paths.get(compiledReportsDirectory));
            Files.createDirectories(Paths.get(reportPDFDirectory));
            Files.createDirectories(Paths.get(reportImagesDirectory));
            Files.createDirectories(Paths.get(subReportDirectory));
            Files.createDirectories(Paths.get(bookReportDirectory));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (mode.equalsIgnoreCase("compile")) {
            try {
                compile_reports(subReportDirectory, compiledReportsDirectory, reportsBaseDirectory, bookReportDirectory);
            } catch (IOException | JRException e) {
                throw new RuntimeException(e);
            }
        } else if (mode.equalsIgnoreCase("pdf")) {
            if (reportsToBuild == null) {
                throw new IllegalArgumentException("You need to specify a report to generate pdf.");
            }

            // Estabelece conexão com o PostgreSQL
            try (Connection conn = DriverManager.getConnection(url, user, password)) {
                // Parâmetros do relatório (se houver)
                Map<String, Object> parameters = new HashMap<>(params);

                parameters.put("IMAGE_DIR", reportImagesDirectory);
                parameters.put("SUBREPORT_DIR", compiledReportsDirectory);
                parameters.put("COMPILED_REPORT_DIR", compiledReportsDirectory);

                Path reportJasperFile = Paths.get(compiledReportsDirectory + reportsToBuild + ".jasper");
                byte[] bytes = generatePDFFromJasper(Files.readAllBytes(reportJasperFile), parameters, conn);

                Path reportPDFFile = Paths.get(reportPDFDirectory + reportPdfOutputName + ".pdf");
                Files.write(reportPDFFile, bytes);
            } catch (JRException | IOException | SQLException e) {
                throw new RuntimeException(e);
            }

        } else {
            throw new IllegalArgumentException("Invalid mode.");
        }
    }

    private static void compile_reports(String subReportDirectory, String compiledReportsDirectory, String reportsBaseDirectory, String bookReportDirectory) throws IOException, JRException {
        // Passa através de todos os arquivos .jrxml de subreports
        DirectoryStream<Path> files = Files.newDirectoryStream(Paths.get(subReportDirectory), "*.jrxml");
        for (Path file : files) {
            if (Files.isRegularFile(file)) {
                byte[] bytes = compileReport(file);
                saveJasperBytesToFile(bytes, compiledReportsDirectory, file.getFileName().toString());
            }
        }

        // Passa através de todos os arquivos .jrxml
        files = Files.newDirectoryStream(Paths.get(reportsBaseDirectory), "*.jrxml");
        for (Path file : files) {
            if (Files.isRegularFile(file)) {
                byte[] bytes = compileReport(file);
                saveJasperBytesToFile(bytes, compiledReportsDirectory, file.getFileName().toString());
            }
        }

        // Passa através de todos os arquivos .jrxml de books
        files = Files.newDirectoryStream(Paths.get(bookReportDirectory), "*.jrxml");
        for (Path file : files) {
            if (Files.isRegularFile(file)) {
                byte[] bytes = compileReport(file);
                saveJasperBytesToFile(bytes, compiledReportsDirectory, file.getFileName().toString());
            }
        }
    }

    private static void savePDFBytesToFile(final byte[] bytes, final String reportPDFDirectory, final String fileName) throws IOException {
        String fileNameWithoutExtension = fileName.substring(0, fileName.lastIndexOf("."));
        Files.write(Paths.get(reportPDFDirectory + fileNameWithoutExtension + ".pdf"), bytes, StandardOpenOption.CREATE);
    }

    private static void saveJasperBytesToFile(final byte[] bytes, final String reportPDFDirectory, final String fileName) throws IOException {
        String fileNameWithoutExtension = fileName.substring(0, fileName.lastIndexOf("."));
        Files.write(Paths.get(reportPDFDirectory + fileNameWithoutExtension + ".jasper"), bytes, StandardOpenOption.CREATE);
    }

    private static byte[] compileReport(Path jasperFile) throws IOException, JRException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        // Compilar o relatório
        JasperCompileManager.compileReportToStream(Files.newInputStream(jasperFile), stream);
        return stream.toByteArray();
    }

    private static byte[] generatePDFFromJasper(byte[] bytes, Map<String, Object> parameters, Connection conn) throws JRException {
        // Preenche o relatório
        JasperPrint jasperPrint = JasperFillManager.fillReport(new ByteArrayInputStream(bytes), parameters, conn);

        // Exporta para PDF
        return JasperExportManager.exportReportToPdf(jasperPrint);
    }
}
