package br.com.paymentprocessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.paymentprocessor.model.Constants;
import br.com.paymentprocessor.model.MessageType;
import br.com.paymentprocessor.model.WorkflowMessage;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Service
public class PaymentsProcessor {
	private final ObjectMapper objectMapper;
	
	private final Logger LOGGER = Logger.getLogger(PaymentsProcessor.class.getName());
	
	private final String REQUEST_QUEUE_NAME;
	
	private final String RESPONSE_QUEUE_NAME;
	
	public PaymentsProcessor(ObjectMapper objectMapper,
			@Value("${aws.request-queue:none}") String requestQueueName,
			@Value("${aws.response-queue:none}") String responseQueueName) {
		this.objectMapper = objectMapper;
		this.REQUEST_QUEUE_NAME = requestQueueName;
		this.RESPONSE_QUEUE_NAME = responseQueueName;
	}
	
	@Scheduled(fixedRate = Constants.TASK_POLL_MILLIS)
	public void ReceivePaymentRequests() throws Exception {
		try {
			SqsClient sqsClient = SqsClient.builder()
	            .region(Region.SA_EAST_1)
	            .build();
			
			GetQueueUrlRequest getRequestQueue = GetQueueUrlRequest.builder()
	            .queueName(REQUEST_QUEUE_NAME)
	            .build();
	
	        String requestQueueUrl = sqsClient.getQueueUrl(getRequestQueue).queueUrl();
			
			// Receive messages from the queue
			ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(requestQueueUrl)
                .messageAttributeNames("All")
                .build();
            List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();
            
            if(messages.isEmpty()) {
            	LOGGER.info("No payments to process...");
            } else {
            	// Handle messages
            	for (Message m : messages) {
            		SendResponse(sqsClient, m);
            	}            	
            }  
        } catch (Exception e) {
        	LOGGER.log(Level.SEVERE, "ERROR PAYMENT PROCESSOR", e);
            throw e;
        }
	}

	private void SendResponse(SqsClient sqsClient, Message m) throws Exception {
		try {
			System.out.println("\n" +m.body());
			
			WorkflowMessage requestMessage = objectMapper.readValue(m.body(), WorkflowMessage.class);
			
			// Send the response to Camunda Engine
            GetQueueUrlRequest getResponseQueue = GetQueueUrlRequest.builder()
                    .queueName(RESPONSE_QUEUE_NAME)
                    .build();
            String responseQueueUrl = sqsClient.getQueueUrl(getResponseQueue).queueUrl();
            
            boolean success = true;
            String topic = (String) requestMessage.getVariables().get(Constants.ATTRIBUTE_ROUTING_KEY);
            
            Map<String, MessageAttributeValue> attributesResponseMessage = new HashMap<>();;
            attributesResponseMessage.put(Constants.ATTRIBUTE_EXTERNAL_TASK_ID, MessageAttributeValue.builder()
            		.stringValue(requestMessage.getExternalTaskId())
                    .dataType("String")
                    .build());
            attributesResponseMessage.put(Constants.ATTRIBUTE_NAME_TYPE, MessageAttributeValue.builder()
            		.stringValue(MessageType.EXTERNAL_TASK.name())
                    .dataType("String")
                    .build());
            attributesResponseMessage.put(Constants.ATTRIBUTE_ROUTING_KEY, MessageAttributeValue.builder()
                    .stringValue(topic)
                    .dataType("String")
                    .build());
            attributesResponseMessage.put(Constants.ATTRIBUTE_REPLY_TO, MessageAttributeValue.builder()
                    .stringValue(responseQueueUrl)
                    .dataType("String")
                    .build());
            attributesResponseMessage.put(Constants.ATTRIBUTE_ROUTING_SUCCESS, MessageAttributeValue.builder()
                    .stringValue(String.valueOf(success))
                    .dataType("String")
                    .build());
            
            WorkflowMessage responseMessage = WorkflowMessage.builder()
            		.businessKey(requestMessage.getBusinessKey())
                    .workflow(requestMessage.getWorkflow())
                    .variables(requestMessage.getVariables())
                    .build();
            String messageString = objectMapper.writeValueAsString(responseMessage);
            
            LOGGER.info("Sending payment response message with body: " + messageString);
            
            SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                .queueUrl(responseQueueUrl)
                .messageBody(messageString)
                .messageAttributes(attributesResponseMessage)
                .delaySeconds(0)
                .build();
            sqsClient.sendMessage(sendMsgRequest);
		} catch(Exception ex) {
			LOGGER.log(Level.SEVERE, "ERROR PAYMENT PROCESSOR: ", ex);
            throw ex;
		}
	}
}
