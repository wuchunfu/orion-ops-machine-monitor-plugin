package com.orion.ops.machine.monitor.metrics.reduce;

import com.orion.ops.machine.monitor.entity.bo.DiskIoUsingBO;
import com.orion.ops.machine.monitor.utils.PathBuilders;
import com.orion.ops.machine.monitor.utils.Utils;
import com.orion.utils.collect.Lists;
import com.orion.utils.collect.Maps;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 磁盘时级数据规约器
 *
 * @author Jiahang Li
 * @version 1.0.0
 * @since 2022/7/4 10:51
 */
@Component
public class DiskHourReduceResolver implements IMetricsHourReduceResolver<DiskIoUsingBO> {

    /**
     * 当前采集信息粒度时
     */
    private final Map<String, String> currentHours;

    /**
     * 当前采集信息
     */
    private final Map<String, List<DiskIoUsingBO>> currentMetrics;

    public DiskHourReduceResolver() {
        this.currentHours = Maps.newMap();
        this.currentMetrics = Maps.newMap();
    }

    @Override
    public void reduce(DiskIoUsingBO data) {
        String seq = data.getSeq();
        String currentHour = Utils.getRangeStartHour(data);
        String prevHour = currentHours.computeIfAbsent(seq, k -> currentHour);
        List<DiskIoUsingBO> list = currentMetrics.computeIfAbsent(seq, k -> Lists.newList());
        // 同一时间
        if (currentHour.equals(prevHour)) {
            list.add(data);
            return;
        }
        // 不同时间则规约
        currentHours.put(seq, currentHour);
        // 计算数据
        DiskIoUsingBO reduceData = new DiskIoUsingBO();
        reduceData.setRc(list.stream().mapToLong(DiskIoUsingBO::getRc).sum());
        reduceData.setRs(list.stream().mapToLong(DiskIoUsingBO::getRs).sum());
        reduceData.setWc(list.stream().mapToLong(DiskIoUsingBO::getWc).sum());
        reduceData.setWs(list.stream().mapToLong(DiskIoUsingBO::getWs).sum());
        reduceData.setUt(list.stream().mapToLong(DiskIoUsingBO::getUt).sum());
        Utils.setReduceHourRange(reduceData, prevHour, currentHour);
        list.clear();
        list.add(data);
        // 拼接到月级数据
        String path = PathBuilders.getDiskMonthDataPath(Utils.getRangeStartMonth(prevHour), seq);
        Utils.appendMetricsData(path, reduceData);
    }

}