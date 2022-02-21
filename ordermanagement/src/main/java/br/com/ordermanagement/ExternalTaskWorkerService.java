package br.com.ordermanagement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.externaltask.ExternalTaskQueryBuilder;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.ordermanagement.model.WorkflowMessage;

@Service
public class ExternalTaskWorkerService {
	private final ExternalTaskService externalTaskService;
    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final String externalTaskTopicArn;
    private final String paymentsResponseQueueUrl;
    
    private final Logger LOGGER = Logger.getLogger(LoggerDelegate.class.getName());
    
    public ExternalTaskWorkerService(ExternalTaskService externalTaskService,
            ObjectMapper objectMapper,
            SnsClient snsClient,
            @Value("${aws.task-topic:none}") String externalTaskTopicArn,
            @Value("${aws.payments-response-queue:none}") String paymentsResponseQueueUrl) {
		this.externalTaskService = externalTaskService;
		this.objectMapper = objectMapper;
		this.snsClient = snsClient;
		this.externalTaskTopicArn = externalTaskTopicArn;
		this.paymentsResponseQueueUrl = paymentsResponseQueueUrl;
	}
    
    @Scheduled(fixedRate = Constants.TASK_POLL_MILLIS)
    @Async("externalTaskExecutor")
    public void processTasks() {
        // Fetch topics
        List<String> availableTopics = externalTaskService.getTopicNames(false, true, true);
        try {
        	LOGGER.info("Topics: {"
    			+ objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(availableTopics) + "}");
            if (availableTopics.isEmpty()) {
            	LOGGER.info("No tasks to send");
                return;
            }
        } catch (JsonProcessingException e) {
        	LOGGER.log(Level.SEVERE, "Error mapping list", e);
        }

        ExternalTaskQueryBuilder externalTaskQueryBuilder =
                externalTaskService.fetchAndLock(Constants.TASK_LOCK_COUNT, Constants.WORKER_NAME);
        for (String topic : availableTopics) {
            externalTaskQueryBuilder.topic(topic, Constants.TASK_TIMEOUT_MILLIS);
        }
        List<LockedExternalTask> externalTasks = externalTaskQueryBuilder.execute();
        for (LockedExternalTask lockedExternalTask : externalTasks) {
            try {
				/* COMMENTED: PLUGIN SNS-SQS COMMUNICATION WITH EXTERNAL TASKS
				 * sendMessage(lockedExternalTask.getTopicName(),
				 * lockedExternalTask.getBusinessKey(),
				 * lockedExternalTask.getProcessDefinitionKey(), lockedExternalTask.getId(),
				 * lockedExternalTask.getVariables());
				 */
            } catch (Exception e) {
            	LOGGER.log(Level.SEVERE, "Error sending SQS message", e);
            }
        }
    }

    private void sendMessage(String topic, String businessKey, String workflow, 
    		String externalTaskId,
    		Map<String, Object> variables) throws Exception {
    	LOGGER.info("Sending SNS message for topic: {" + topic + "}");
    	
        Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put(Constants.ATTRIBUTE_ROUTING_KEY, MessageAttributeValue.builder()
                .stringValue(topic)
                .dataType("String")
                .build());
        
        WorkflowMessage workflowMessage = WorkflowMessage.builder()
            .businessKey(businessKey)
            .workflow(workflow)
            .externalTaskId(externalTaskId)
            .externalTaskName("")
            .responseQueueUrl(paymentsResponseQueueUrl)
            .variables(variables)
            .build();
        
        snsClient.publish(PublishRequest.builder()
            .topicArn(externalTaskTopicArn)
            .message(objectMapper.writeValueAsString(workflowMessage))
            .messageAttributes(messageAttributes)
            .messageGroupId(topic)
            .build());
    }
}
