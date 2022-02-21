package br.com.ordermanagement;

import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.impl.bpmn.behavior.ExternalTaskActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParse;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.impl.util.xml.Element;
import org.springframework.beans.factory.annotation.Value;

public class ProgressLoggingSupportParseListener extends AbstractBpmnParseListener {
	public final String requestsTopicTopicArn;
	
	public ProgressLoggingSupportParseListener(@Value("${aws.requests-topic:none}") String requestsTopicTopicArn) {
		this.requestsTopicTopicArn = requestsTopicTopicArn;
	}

	  // parse given service task to get the attributes of the property extension elements 

	  @Override
	  public void parseServiceTask(Element serviceTaskElement, ScopeImpl scope, ActivityImpl activity) {
		  ActivityBehavior activityBehavior = activity.getActivityBehavior();
		  if (activityBehavior instanceof ExternalTaskActivityBehavior) {
			  //ExternalTaskActivityBehavior externalTaskActivityBehavior = (ExternalTaskActivityBehavior) activityBehavior;
			  String topicName = serviceTaskElement.attributeNS(BpmnParse.CAMUNDA_BPMN_EXTENSIONS_NS, 
					  BpmnParse.PROPERTYNAME_EXTERNAL_TASK_TOPIC);
			  activity.addListener(ExecutionListener.EVENTNAME_START, new ProgressLoggingExecutionListener(topicName,
					  this.requestsTopicTopicArn));
	  	}
  	}
}
