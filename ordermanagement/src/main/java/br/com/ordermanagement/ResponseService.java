package br.com.ordermanagement;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.externaltask.ExternalTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.ordermanagement.model.MessageType;
import br.com.ordermanagement.model.WorkflowMessage;
import br.com.ordermanagement.repository.ExternalTaskRepository;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

@Service
public class ResponseService {
	private final ExternalTaskService externalTaskService;
    private final ExternalTaskRepository externalTaskRepository;
    private final ExternalTaskWorkerService externalTaskWorkerService;
    private final ObjectMapper objectMapper;
    private final SqsClient sqsClient;
    private final String responseQueueUrl;
    
    private final Logger LOGGER = Logger.getLogger(LoggerDelegate.class.getName());

    public ResponseService(ExternalTaskService externalTaskService,
                           ExternalTaskRepository externalTaskRepository,
                           ExternalTaskWorkerService externalTaskWorkerService,
                           ObjectMapper objectMapper,
                           SqsClient sqsClient,
                           @Value("${aws.response-queue:none}") String responseQueueUrl) {
        this.externalTaskService = externalTaskService;
        this.externalTaskRepository = externalTaskRepository;
        this.externalTaskWorkerService = externalTaskWorkerService;
        this.objectMapper = objectMapper;
        this.sqsClient = sqsClient;
        this.responseQueueUrl = responseQueueUrl;

        LOGGER.info("Queue: {" + responseQueueUrl + "}");
    }

    @Scheduled(fixedDelay = 1000)
    @Async("responseQueueExecutor")
    public void pollResponseQueue() {
        try {
        	LOGGER.info("Long-polling queue: {" + responseQueueUrl + "}");
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(responseQueueUrl)
                    .maxNumberOfMessages(10)
                    .messageAttributeNames("All")
                    .build();
            ReceiveMessageResponse receiveMessageResponse = sqsClient.receiveMessage(receiveMessageRequest);
            for (Message message : receiveMessageResponse.messages()) {
                handleMessage(message);
            }
        } catch (Exception e) {
        	LOGGER.log(Level.SEVERE, "Error receiving messages", e);
        }
    }

    private void handleMessage(Message message) throws Exception {
    	LOGGER.info(String.format("Message: %s", message.body()));
    	
        WorkflowMessage workflowMessage = objectMapper.readValue(message.body(), WorkflowMessage.class);
        
        Map<String, MessageAttributeValue> attributes = message.messageAttributes();
        
        MessageType type = MessageType.fromString(attributes.getOrDefault(Constants.ATTRIBUTE_NAME_TYPE,
                MessageAttributeValue.builder().build()).stringValue());
        boolean success = Boolean.parseBoolean(attributes.getOrDefault(Constants.ATTRIBUTE_ROUTING_SUCCESS,
                MessageAttributeValue.builder().build()).stringValue());
        String routingKey = attributes.getOrDefault(Constants.ATTRIBUTE_ROUTING_KEY,
                MessageAttributeValue.builder().build()).stringValue();
		/*
		 * String externalTaskId = externalTaskRepository.getExternalTaskId(workflowMessage.getBusinessKey(),
		 * routingKey);
		 */
        String externalTaskId = attributes.getOrDefault(Constants.ATTRIBUTE_EXTERNAL_TASK_ID,
                MessageAttributeValue.builder().build()).stringValue();
        
        LOGGER.info("Message: {" + workflowMessage + "}");
        LOGGER.info("Type: {" + attributes.get(Constants.ATTRIBUTE_NAME_TYPE) + "}");
        LOGGER.info("Success: {" + success + "}");
        LOGGER.info("Routing-key: {" + routingKey + "}");
        LOGGER.info("Task id: {" + externalTaskId + "}");
        
        if (externalTaskId != null) {
            if (type == MessageType.EXTERNAL_TASK) {
                if (success) {
                    // Complete task
                	LOGGER.info("Completing task: {" + externalTaskId + "}");
                    externalTaskService.complete(externalTaskId,
                            Constants.WORKER_NAME,
                            workflowMessage.getVariables());

                } else {
                    // Fail task
                	LOGGER.info("Failing task: {" + externalTaskId + "}");
                    ExternalTask externalTask = externalTaskService.createExternalTaskQuery()
                            .externalTaskId(externalTaskId)
                            .singleResult();
                    externalTaskService.handleFailure(externalTaskId,
                            Constants.WORKER_NAME,
                            "TODO: Error message",
                            Math.max(0, externalTask.getRetries() - 1),
                            3000);
                }
                // Process external tasks
                LOGGER.info("Calling process tasks from ResponseService");
                externalTaskWorkerService.processTasks();
            } else {
            	LOGGER.log(Level.SEVERE, String.format("Unknown message type: %s", message));
            }
        } else {
            LOGGER.warning(externalTaskId);
        }
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(responseQueueUrl)
                .receiptHandle(message.receiptHandle())
                .build());
    }
}
