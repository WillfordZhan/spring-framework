Certainly! Here's how you can add data rows from a large CSV file into an Apache Ignite cache using the thin client and `IgniteDataStreamer` without causing memory overflow.

### Solution Explanation:
1. **Thin Client Setup**: First, you need to ensure that your Ignite thin client is connected to the Ignite server using a configuration file or programmatically.
   
2. **DataStreamer Configuration**: Use an `IgniteDataStreamer` to stream data efficiently into an Ignite cache.

3. **Efficient CSV Parsing**: Read the CSV file line by line to avoid loading everything into memory at once.

4. **Batch Writing and Flushing**: Write data in batches, flushing after reaching a specified limit to control memory usage.

### Example Code:

```java
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteDataStreamer;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.client.IgniteClientConfiguration;
import org.apache.ignite.Ignition;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class IgniteCsvToCache {
    public static void main(String[] args) {
        // Configure Ignite thin client
        IgniteClientConfiguration clientConfig = new IgniteClientConfiguration()
                .setAddresses("127.0.0.1:10800"); // Replace with your server address

        // Start Ignite thin client
        try (IgniteClient ignite = Ignition.startClient(clientConfig)) {
            // Initialize DataStreamer for the specified cache
            try (IgniteDataStreamer<Integer, MyData> streamer = ignite.dataStreamer("yourCacheName")) {
                streamer.allowOverwrite(true);
                streamer.autoFlushFrequency(5000); // Auto flush every 5 seconds

                // Open and read the CSV file line by line
                try (BufferedReader reader = new BufferedReader(new FileReader("path/to/your/large.csv"))) {
                    String line;
                    int batchSize = 1000; // Adjust batch size according to your needs
                    int counter = 0;

                    // Process each line of the CSV file
                    while ((line = reader.readLine()) != null) {
                        MyData data = parseCsvLine(line);

                        // Stream data into the cache
                        streamer.addData(data.getId(), data);

                        // Manually flush when reaching batch size
                        if (++counter % batchSize == 0) {
                            streamer.flush();
                        }
                    }

                    // Flush remaining data
                    streamer.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Parse CSV line to create your MyData object
    private static MyData parseCsvLine(String line) {
        String[] parts = line.split(",");
        int id = Integer.parseInt(parts[0]);
        String value = parts[1]; // Example field - replace with your actual fields
        return new MyData(id, value);
    }

    // Example class representing your data object
    private static class MyData {
        private final int id;
        private final String value;

        public MyData(int id, String value) {
            this.id = id;
            this.value = value;
        }

        public int getId() {
            return id;
        }

        public String getValue() {
            return value;
        }
    }
}
```

### Notes:
- **Adjust the Connection**: Make sure your Ignite thin client connects correctly to your Ignite cluster by updating the address.
- **Batch Size**: Adjust the batch size (`batchSize`) according to your memory and performance requirements.
- **Data Parsing**: Replace `MyData` with your actual data structure to match the contents of the CSV file.

Feel free to ask for more details or modifications if required!