package br.com.ordermanagement;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.externaltask.ExternalTask;
import org.camunda.bpm.engine.impl.cfg.TransactionState;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.ordermanagement.model.WorkflowMessage;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class ProgressLoggingExecutionListener implements ExecutionListener {
	
	private final Logger LOGGER = Logger.getLogger(this.getClass().getName());
	private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    public String requestsTopicTopicArn;
	private String topicName;

	// constructor with extension property value as parameter
	public ProgressLoggingExecutionListener(String topicName, String requestsTopicTopicArn) {
		this.topicName = topicName;
		this.objectMapper = new ObjectMapper();
		this.snsClient = SnsClient.create();
		this.requestsTopicTopicArn = requestsTopicTopicArn;
	}

	// notify method is executed when Execution Listener is called
	@Override
	public void notify(DelegateExecution execution) throws Exception {
		LOGGER.info("EXTERNAL TASK TOPIC RUNNING: " + topicName);
		Context.getCommandContext().getTransactionContext()
			.addTransactionListener(TransactionState.COMMITTED, commandContext ->{
				
				ExternalTask externalTask = execution.getProcessEngineServices()
					.getExternalTaskService()
					.createExternalTaskQuery()
					.executionId(execution.getId())
					.singleResult();
			
				if (externalTask != null) {
					try {
						sendMessage(externalTask, execution.getVariables());
					} catch (Exception e) {
						LOGGER.log(Level.SEVERE, "Error sending SQS message", e);
					}
				} else {
					LOGGER.info("External for model {" + execution.getBpmnModelElementInstance().getName() + "} not found");
				}
		});
		LOGGER.info("EXTERNAL TASK TOPIC COMPLETED: " + topicName);
	}
	  
	private void sendMessage(ExternalTask externalTask, Map<String, Object> variables) throws Exception {
		LOGGER.info("Sending SNS message for topic: {" + topicName + "}");
		
		Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
		messageAttributes.put(Constants.ATTRIBUTE_ROUTING_KEY, MessageAttributeValue.builder()
		        .stringValue(topicName)
		        .dataType("String")
		        .build());
		String businessKey = externalTask.getBusinessKey();
		String workflow = externalTask.getProcessDefinitionKey();
		String topicName = externalTask.getTopicName();
		String externalTaskId = externalTask.getId();
		WorkflowMessage workflowMessage = WorkflowMessage.builder()
	            .businessKey(businessKey)
	            .workflow(workflow)
	            .externalTaskId(externalTaskId)
	            .externalTaskName("")
	            .responseQueueUrl(topicName + "-queue-responses")
	            .variables(variables)
	            .build();
		
		snsClient.publish(PublishRequest.builder()
		        .topicArn(requestsTopicTopicArn)
		        .message(objectMapper.writeValueAsString(workflowMessage))
		        .messageAttributes(messageAttributes)
		        .messageGroupId(topicName)
		        .build());
  	}
}
