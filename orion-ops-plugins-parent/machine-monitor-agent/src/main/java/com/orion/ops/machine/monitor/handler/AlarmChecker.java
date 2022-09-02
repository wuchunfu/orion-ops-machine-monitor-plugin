package com.orion.ops.machine.monitor.handler;

import com.alibaba.fastjson.JSON;
import com.orion.lang.utils.collect.Maps;
import com.orion.ops.machine.monitor.constant.MachineAlarmType;
import com.orion.ops.machine.monitor.constant.PropertiesConst;
import com.orion.ops.machine.monitor.entity.request.MachineAlarmRequest;
import com.orion.ops.plugin.common.handler.http.HttpApiRequest;
import com.orion.ops.plugin.common.handler.http.OrionOpsExposeHttpApi;
import com.orion.ops.plugin.common.handler.http.OrionOpsExposeHttpApiRequester;
import com.orion.ops.plugin.common.handler.http.vo.MachineAlarmConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

/**
 * 报警推送
 *
 * @author Jiahang Li
 * @version 1.0.0
 * @since 2022/8/31 19:06
 */
@Slf4j
@Component
public class AlarmChecker {

    /**
     * 报警配置
     */
    @Getter
    private final Map<MachineAlarmType, MachineAlarmConfig> alarmContext = Maps.newMap();

    /**
     * 连续报警次数
     * <p>
     * key: machineId_type
     * value: count
     */
    private final Map<String, Integer> alarmCountMap = Maps.newMap();

    /**
     * 上次报警时间
     * <p>
     * key: machineId_type
     * value: time
     */
    private final Map<String, Long> lastAlarmTimeMap = Maps.newMap();

    /**
     * 检测报警
     *
     * @param type  type
     * @param value value
     */
    public void check(MachineAlarmType type, Double value) {
        MachineAlarmConfig config = alarmContext.get(type);
        if (config == null) {
            return;
        }
        Long machineId = PropertiesConst.MACHINE_ID;
        String key = machineId + "_" + type.getType();
        // 未达到阈值 重置
        if (value.compareTo(config.getAlarmThreshold()) < 0) {
            alarmCountMap.put(key, 0);
            return;
        }
        // 达到阈值 递增
        Integer count = alarmCountMap.computeIfAbsent(key, k -> 0) + 1;
        alarmCountMap.put(key, count);
        log.info("{}达到报警阈值 value: {}, count: {}", type, value, count);
        // 未达到通知阈值
        if (count.compareTo(config.getTriggerThreshold()) < 0) {
            return;
        }
        // 检测是否处于沉默阶段
        long now = System.currentTimeMillis();
        Long alarmTime = lastAlarmTimeMap.get(key);
        if (alarmTime != null) {
            long silenceTimestamp = config.getNotifySilence() * 60 * 1000;
            // 处于沉默阶段则不通知
            if (now - alarmTime < silenceTimestamp) {
                return;
            }
        }
        // 执行通知
        MachineAlarmRequest alarmRequest = new MachineAlarmRequest();
        alarmRequest.setMachineId(PropertiesConst.MACHINE_ID);
        alarmRequest.setType(type.getType());
        alarmRequest.setAlarmValue(value);
        alarmRequest.setAlarmTime(new Date(now));
        log.info("触发报警通知 req: {}", JSON.toJSONString(alarmRequest));
        try {
            HttpApiRequest request = OrionOpsExposeHttpApiRequester.create(OrionOpsExposeHttpApi.TRIGGER_MACHINE_ALARM);
            String resp = request.jsonBody(alarmRequest).await().getBodyString();
            lastAlarmTimeMap.put(key, now);
            log.info("触发报警通知-完成 resp: {}", resp);
        } catch (Exception e) {
            log.error("触发报警通知-失败", e);
        }
    }

}
