package com.entity.resolution.graph;

/**
 * Configuration for {@link SimpleGraphConnectionPool}.
 */
public class PoolConfig {

    private final int maxTotal;
    private final int maxIdle;
    private final int minIdle;
    private final long maxWaitMillis;
    private final boolean testOnBorrow;
    private final String host;
    private final int port;
    private final String graphName;

    private PoolConfig(Builder builder) {
        this.maxTotal = builder.maxTotal;
        this.maxIdle = builder.maxIdle;
        this.minIdle = builder.minIdle;
        this.maxWaitMillis = builder.maxWaitMillis;
        this.testOnBorrow = builder.testOnBorrow;
        this.host = builder.host;
        this.port = builder.port;
        this.graphName = builder.graphName;
    }

    public int getMaxTotal() { return maxTotal; }
    public int getMaxIdle() { return maxIdle; }
    public int getMinIdle() { return minIdle; }
    public long getMaxWaitMillis() { return maxWaitMillis; }
    public boolean isTestOnBorrow() { return testOnBorrow; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getGraphName() { return graphName; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int maxTotal = 20;
        private int maxIdle = 10;
        private int minIdle = 2;
        private long maxWaitMillis = 5000;
        private boolean testOnBorrow = true;
        private String host = "localhost";
        private int port = 6379;
        private String graphName = "entity-resolution";

        public Builder maxTotal(int maxTotal) {
            if (maxTotal <= 0) throw new IllegalArgumentException("maxTotal must be > 0");
            this.maxTotal = maxTotal;
            return this;
        }

        public Builder maxIdle(int maxIdle) {
            if (maxIdle < 0) throw new IllegalArgumentException("maxIdle must be >= 0");
            this.maxIdle = maxIdle;
            return this;
        }

        public Builder minIdle(int minIdle) {
            if (minIdle < 0) throw new IllegalArgumentException("minIdle must be >= 0");
            this.minIdle = minIdle;
            return this;
        }

        public Builder maxWaitMillis(long maxWaitMillis) {
            if (maxWaitMillis <= 0) throw new IllegalArgumentException("maxWaitMillis must be > 0");
            this.maxWaitMillis = maxWaitMillis;
            return this;
        }

        public Builder testOnBorrow(boolean testOnBorrow) {
            this.testOnBorrow = testOnBorrow;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder graphName(String graphName) {
            this.graphName = graphName;
            return this;
        }

        public PoolConfig build() {
            if (maxIdle > maxTotal) {
                throw new IllegalArgumentException("maxIdle cannot exceed maxTotal");
            }
            if (minIdle > maxIdle) {
                throw new IllegalArgumentException("minIdle cannot exceed maxIdle");
            }
            return new PoolConfig(this);
        }
    }

    @Override
    public String toString() {
        return "PoolConfig{" +
                "maxTotal=" + maxTotal +
                ", maxIdle=" + maxIdle +
                ", minIdle=" + minIdle +
                ", maxWaitMillis=" + maxWaitMillis +
                ", testOnBorrow=" + testOnBorrow +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", graphName='" + graphName + '\'' +
                '}';
    }
}
