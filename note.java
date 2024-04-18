import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeJobResult;
import org.apache.ignite.compute.ComputeTaskSplitAdapter;
import org.apache.ignite.configuration.IgniteConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CacheMemoryStatisticsCollector {
    public static void main(String[] args) {
        try (Ignite ignite = Ignition.start("config/ignite-config.xml")) {
            Map<String, List<CacheNodeStats>> totalStats = ignite.compute().execute(CacheMemoryStatsTask.class, ignite.cacheNames());
            totalStats.forEach((cacheName, statsList) -> {
                System.out.printf("Cache: %s%n", cacheName);
                statsList.forEach(stats ->
                    System.out.printf("Hostname: %s, Total Allocated Size: %d, Total Entries: %d%n",
                                      stats.getHostname(), stats.getTotalAllocatedSize(), stats.getTotalEntriesCount()));
            });
        }
    }

    private static class CacheMemoryStatsTask extends ComputeTaskSplitAdapter<Collection<String>, Map<String, List<CacheNodeStats>>> {
        @Override
        public Map<String, List<CacheNodeStats>> reduce(List<ComputeJobResult> results) {
            Map<String, List<CacheNodeStats>> totalStats = new HashMap<>();
            for (ComputeJobResult res : results) {
                Map<String, CacheNodeStats> nodeStats = res.getData();
                nodeStats.forEach((cacheName, stats) ->
                    totalStats.computeIfAbsent(cacheName, k -> new ArrayList<>()).add(stats));
            }
            return totalStats;
        }

        @Override
        public Collection<? extends ComputeJob> split(int gridSize, Collection<String> cacheNames) {
            List<ComputeJob> jobs = new ArrayList<>();
            Collection<ClusterNode> nodes = ignite.cluster().nodes();
            for (ClusterNode node : nodes) {
                for (String cacheName : cacheNames) {
                    jobs.add(new ComputeJob() {
                        @Override
                        public Object execute() {
                            long allocatedSize = ignite.cache(cacheName).metrics().getOffHeapAllocatedSize();
                            int entriesCount = ignite.cache(cacheName).metrics().getSize();
                            String hostname = node.hostNames().iterator().next();
                            CacheNodeStats stats = new CacheNodeStats(allocatedSize, entriesCount, hostname);
                            return Map.of(cacheName, stats);
                        }

                        @Override
                        public void cancel() {
                            // Handle job cancellation if necessary
                        }
                    });
                }
            }
            return jobs;
        }
    }

    private static class CacheNodeStats {
        private long totalAllocatedSize;
        private int totalEntriesCount;
        private String hostname;

        public CacheNodeStats(long totalAllocatedSize, int totalEntriesCount, String hostname) {
            this.totalAllocatedSize = totalAllocatedSize;
            this.totalEntriesCount = totalEntriesCount;
            this.hostname = hostname;
        }

        public long getTotalAllocatedSize() {
            return totalAllocatedSize;
        }

        public int getTotalEntriesCount() {
            return totalEntriesCount;
        }

        public String getHostname() {
            return hostname;
        }
    }
}