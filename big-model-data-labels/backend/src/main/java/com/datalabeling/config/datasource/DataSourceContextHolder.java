package com.datalabeling.config.datasource;

/**
 * 数据源上下文持有者（基于 ThreadLocal）
 * 用于在多线程环境中动态切换数据源
 *
 * @author DataLabeling
 * @since 2025-01-03
 */
public class DataSourceContextHolder {

    /**
     * 数据源标识的 ThreadLocal
     * 格式：
     * - "main": 主数据源
     * - "external_{syncConfigId}": 外部数据源
     */
    private static final ThreadLocal<String> contextHolder = new ThreadLocal<>();

    /**
     * 标记是否强制使用外部数据源的 ThreadLocal
     */
    private static final ThreadLocal<Boolean> forceExternal = new ThreadLocal<>();

    /**
     * 主数据源标识常量
     */
    public static final String MAIN_DATA_SOURCE = "main";

    /**
     * 外部数据源标识前缀
     */
    public static final String EXTERNAL_DATA_SOURCE_PREFIX = "external_";

    /**
     * 设置数据源标识
     *
     * @param key 数据源标识（格式：external_{syncConfigId} 或 main）
     */
    public static void setDataSourceKey(String key) {
        contextHolder.set(key);
    }

    /**
     * 获取当前数据源标识
     *
     * @return 当前数据源标识，如果未设置则返回 null
     */
    public static String getDataSourceKey() {
        return contextHolder.get();
    }

    /**
     * 清除数据源标识
     * 使用完数据源后必须调用此方法，避免内存泄漏
     */
    public static void clearDataSourceKey() {
        contextHolder.remove();
        forceExternal.remove();
    }

    /**
     * 标记强制使用外部数据源
     * 当设置为 true 时，即使当前没有设置数据源标识，也会使用外部数据源
     *
     * @param force 是否强制使用外部数据源
     */
    public static void setForceExternal(boolean force) {
        forceExternal.set(force);
    }

    /**
     * 是否强制使用外部数据源
     *
     * @return true 表示强制使用外部数据源
     */
    public static Boolean isForceExternal() {
        Boolean force = forceExternal.get();
        return force != null && force;
    }

    /**
     * 判断当前是否使用外部数据源
     *
     * @return true 表示当前使用外部数据源
     */
    public static boolean isExternalDataSource() {
        String key = getDataSourceKey();
        return key != null && key.startsWith(EXTERNAL_DATA_SOURCE_PREFIX);
    }

    /**
     * 判断当前是否使用主数据源
     *
     * @return true 表示当前使用主数据源
     */
    public static boolean isMainDataSource() {
        String key = getDataSourceKey();
        return MAIN_DATA_SOURCE.equals(key);
    }

    /**
     * 从外部数据源标识中提取 SyncConfig ID
     *
     * @param key 外部数据源标识（格式：external_{syncConfigId}）
     * @return SyncConfig ID，如果不是外部数据源标识则返回 null
     */
    public static Integer extractSyncConfigId(String key) {
        if (key != null && key.startsWith(EXTERNAL_DATA_SOURCE_PREFIX)) {
            try {
                return Integer.parseInt(key.substring(EXTERNAL_DATA_SOURCE_PREFIX.length()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 构建 SyncConfig 对应的外部数据源标识
     *
     * @param syncConfigId SyncConfig ID
     * @return 外部数据源标识
     */
    public static String buildExternalDataSourceKey(Integer syncConfigId) {
        return EXTERNAL_DATA_SOURCE_PREFIX + syncConfigId;
    }
}
