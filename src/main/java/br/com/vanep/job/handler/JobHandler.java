package br.com.vanep.job.handler;

import br.com.vanep.job.enums.JobType;

public interface JobHandler<T> {

  JobType type();

  Class<T> payloadType();

  void perform(T payload);
}
