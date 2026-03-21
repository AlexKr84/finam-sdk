package ru.finam.trade.hedging;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class HedgingStatsScheduler {
  private final List<HedgingStats> HedgingStatsList;

  public HedgingStatsScheduler(Map<String, HedgingStats> hedgingStatsMap) {
    HedgingStatsList = hedgingStatsMap.values().stream().toList();
  }

  @Scheduled(cron = "0 0 3 * * *")
  public void updateStats() {
    HedgingStatsList.forEach(HedgingStats::updateStats);
  }

  @Scheduled(fixedDelay = 1000)
  public void notifyMaxMinDiff() {
    HedgingStatsList.forEach(HedgingStats::notifyMaxMinDiff);
  }
}
