package br.com.vanep.job.handler;

import br.com.vanep.job.enums.JobType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JobHandlerRegistry {

  private final Map<JobType, JobHandler<?>> handlers = new EnumMap<>(JobType.class);

  public JobHandlerRegistry(List<JobHandler<?>> all) {
    for (JobHandler<?> handler : all) {
      JobHandler<?> previous = handlers.putIfAbsent(handler.type(), handler);
      if (previous != null) {
        throw new IllegalStateException(
            "Two handlers registered for job type %s: %s and %s"
                .formatted(
                    handler.type(),
                    previous.getClass().getSimpleName(),
                    handler.getClass().getSimpleName()));
      }
    }
  }

  public Optional<JobHandler<?>> findHandler(JobType type) {
    return Optional.ofNullable(handlers.get(type));
  }
}
