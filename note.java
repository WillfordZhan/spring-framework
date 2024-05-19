import org.apache.commons.csv.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CsvSplitterOptimized {
    public static void main(String[] args) {
        String inputFile = "path/to/large/input.csv";  // Path to the large CSV file
        String outputDir = "path/to/output/directory/"; // Output directory
        int numberOfFiles = 10;  // Number of smaller files to create
        
        splitCsv(inputFile, outputDir, numberOfFiles);
    }

    public static void splitCsv(String inputFile, String outputDir, int numberOfFiles) {
        // Ensure output directory exists
        try {
            Files.createDirectories(Paths.get(outputDir));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        List<CSVPrinter> printers = new ArrayList<>(numberOfFiles);
        try (Reader in = new FileReader(inputFile)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(in);

            long linesPerFile = (countLines(inputFile) - 1) / numberOfFiles; // subtract 1 for header in count if needed
            long lineCount = 0;
            int fileCounter = 0;

            for (CSVRecord record : records) {
                if (lineCount % linesPerFile == 0 && lineCount / linesPerFile < numberOfFiles) {
                    String outputFile = String.format("%soutput-%d.csv", outputDir, fileCounter);
                    printers.add(new CSVPrinter(new FileWriter(outputFile), CSVFormat.DEFAULT));
                    fileCounter++;
                }
                if (!printers.isEmpty()) {
                    printers.get(printers.size() - 1).printRecord(record);
                }
                lineCount++;
            }

            // Close all printers
            for (CSVPrinter printer : printers) {
                printer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static long countLines(String inputFile) throws IOException {
        try (InputStream is = new BufferedInputStream(new FileInputStream(inputFile))) {
            byte[] c = new byte[1024];
            long count = 0;
            int readChars;
            boolean empty = true;
            while ((readChars = is.read(c)) != -1) {
                empty = false;
                for (int i = 0; i < readChars; i++) {
                    if (c[i] == '\n') {
                        count++;
                    }
                }
            }
            return (count == 0 && !empty) ? 1 : count + 1;
        }
    }
}
