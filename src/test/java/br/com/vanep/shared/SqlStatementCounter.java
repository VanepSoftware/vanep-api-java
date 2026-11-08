package br.com.vanep.shared;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

public class SqlStatementCounter {

  private final Statistics statistics;

  public SqlStatementCounter(EntityManagerFactory entityManagerFactory) {
    this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
  }

  public long countStatements(Runnable action) {
    statistics.clear();
    statistics.setStatisticsEnabled(true);
    try {
      action.run();
      return statistics.getPrepareStatementCount();
    } finally {
      statistics.setStatisticsEnabled(false);
    }
  }
}
