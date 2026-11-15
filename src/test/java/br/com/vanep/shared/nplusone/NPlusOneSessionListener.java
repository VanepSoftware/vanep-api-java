package br.com.vanep.shared.nplusone;

import org.hibernate.SessionEventListener;

/**
 * One per Hibernate session ({@code hibernate.session.events.auto}); see {@link NPlusOneDetector}.
 */
public final class NPlusOneSessionListener implements SessionEventListener {

  private final NPlusOneDetector.SessionLog log = new NPlusOneDetector.SessionLog();

  public NPlusOneSessionListener() {
    NPlusOneDetector.SESSIONS.get().push(log);
  }

  @Override
  public void end() {
    NPlusOneDetector.SESSIONS.get().remove(log);
    log.report();
  }
}
