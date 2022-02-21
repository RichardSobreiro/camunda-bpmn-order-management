package br.com.paymentprocessor.model;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class WorkflowMessage {
	private String businessKey;
    private String workflow;
    private Map<String, Object> variables;
    private String externalTaskId;
    private String externalTaskName;
    private String responseQueueUrl;
}
