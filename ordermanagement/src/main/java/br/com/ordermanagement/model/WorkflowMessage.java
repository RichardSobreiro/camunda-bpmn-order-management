package br.com.ordermanagement.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class WorkflowMessage {
	public String businessKey;
	public String workflow;
    public Map<String, Object> variables;
    public String externalTaskId;
    public String externalTaskName;
    public String responseQueueUrl;
}
