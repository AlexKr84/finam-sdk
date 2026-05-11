package ru.finam.trade.hedging;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class HedgingStatsScheduler {
  private final List<HedgingStats> hedgingStatsList;

  public HedgingStatsScheduler(Map<String, HedgingStats> hedgingStatsMap) {
    hedgingStatsList = hedgingStatsMap.values().stream().toList();
  }

  @Scheduled(cron = "0 1 7 * * *")
  public void updateStats() {
    hedgingStatsList.forEach(HedgingStats::updateStats);
  }

  @Scheduled(fixedDelay = 1000)
  public void notifyMaxMinDiff() {
    hedgingStatsList.forEach(HedgingStats::notifyMaxMinDiff);
  }
}
