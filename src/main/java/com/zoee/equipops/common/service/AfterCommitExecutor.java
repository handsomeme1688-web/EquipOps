package com.zoee.equipops.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Slf4j
public class AfterCommitExecutor {

    public void execute(String operation, Runnable action) {
        Runnable safeAction = () -> {
            try {
                action.run();
            } catch (RuntimeException exception) {
                // 数据库已提交时，缓存清理失败不能伪装成业务回滚；后续由日志/补偿处理。
                log.error("after_commit_action_failed operation={}", operation, exception);
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeAction.run();
                }
            });
            return;
        }
        safeAction.run();
    }
}
