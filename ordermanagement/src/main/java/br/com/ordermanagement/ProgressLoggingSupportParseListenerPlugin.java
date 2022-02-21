package br.com.ordermanagement;

import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.springframework.beans.factory.annotation.Value;

public class ProgressLoggingSupportParseListenerPlugin extends AbstractProcessEnginePlugin {

	public final String requestsTopicTopicArn;
	
	public ProgressLoggingSupportParseListenerPlugin(@Value("${aws.requests-topic:none}") String requestsTopicTopicArn) {
		this.requestsTopicTopicArn = requestsTopicTopicArn;
	}
	
	  @Override
	  public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) { 
	    // get all existing preParseListeners
	    List<BpmnParseListener> preParseListeners = processEngineConfiguration.getCustomPreBPMNParseListeners();
	    
	    if(preParseListeners == null) {
	      // if no preParseListener exists, create new list
	      preParseListeners = new ArrayList<BpmnParseListener>();
	      processEngineConfiguration.setCustomPreBPMNParseListeners(preParseListeners);
	    }
	    
	    // add new BPMN Parse Listener
	    preParseListeners.add(new ProgressLoggingSupportParseListener(this.requestsTopicTopicArn));
	  }
	}
