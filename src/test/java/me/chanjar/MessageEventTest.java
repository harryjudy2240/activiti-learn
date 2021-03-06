package me.chanjar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.activiti.engine.ActivitiException;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.task.Task;
import org.activiti.engine.test.ActivitiRule;
import org.activiti.engine.test.Deployment;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:springTypicalUsageTest-context.xml")
public class MessageEventTest {
    
    @Autowired
    private RepositoryService repositoryService;
  
    @Autowired
    private RuntimeService runtimeService;
    
    @Autowired
    private TaskService taskService;
    
    @Autowired
    @Rule
    public ActivitiRule activitiSpringRule;
    
    @Test
    @Deployment(resources = "me/chanjar/message/message-start-event.bpmn")
    public void messageStartEvent() {
      String processDefinitionKey = "message-start-event";
      runtimeService.startProcessInstanceByMessage("msg");
      
      // 完成一个任务
      Task task1 = taskService.createTaskQuery().processDefinitionKey(processDefinitionKey).taskDefinitionKey("usertask1").singleResult();
      taskService.complete(task1.getId());
      
      // 判断process instance已经结束
      assertEquals(0, runtimeService.createProcessInstanceQuery().processDefinitionKey(processDefinitionKey).count());
    }
    
    @Test(expected=ActivitiException.class)
    public void duplicateMessageStartEventCrossProcessDefinitions() {
      repositoryService
        .createDeployment()
        .addClasspathResource("me/chanjar/message/message-start-event.bpmn")
        .addClasspathResource("me/chanjar/message/message-start-event-2.bpmn")
        .deploy();
    }
    
    @Test(expected=ActivitiException.class)
    public void duplicateMessageStartEventInSameProcessDefinition() {
      repositoryService
      .createDeployment()
      .addClasspathResource("me/chanjar/message/message-start-event-duplicate.bpmn")
      .deploy();
    }
    
    @Test
    @Deployment(resources="me/chanjar/message/message-intermediate-event-catch.bpmn")
    public void messageIntermediateCatch() {
      String processDefinitionKey = "message-intermediate-event-catch";
      runtimeService.startProcessInstanceByKey(processDefinitionKey);
      runtimeService.startProcessInstanceByKey(processDefinitionKey);

      assertEquals(2, runtimeService.createProcessInstanceQuery().processDefinitionKey(processDefinitionKey).count());

      sendMessage(processDefinitionKey);
      
      // 判断process instance已经结束
      assertEquals(0, runtimeService.createProcessInstanceQuery().processDefinitionKey(processDefinitionKey).count());
    }
    
    @Test
    @Deployment(resources="me/chanjar/message/message-boundary-catch.bpmn")
    public void messageBoundaryCatch() {
      String processDefinitionKey = "message-boundary-catch";
      runtimeService.startProcessInstanceByKey(processDefinitionKey);
      
      // 只有当当前execution在usertask1的时候才能够发送消息，否则是没用的。
      sendMessage(processDefinitionKey);
      
      Task task1 = taskService.createTaskQuery().processDefinitionKey(processDefinitionKey).taskDefinitionKey("usertask1").singleResult();
      assertNull(task1);
      
      Task task2 = taskService.createTaskQuery().processDefinitionKey(processDefinitionKey).taskDefinitionKey("usertask2").singleResult();
      taskService.complete(task2.getId());
      
      // 判断process instance已经结束
      assertEquals(0, runtimeService.createProcessInstanceQuery().processDefinitionKey(processDefinitionKey).count());
    }
    
    private void sendMessage(String processDefinitionKey) {
      List<Execution> executions = runtimeService
          .createExecutionQuery()
          .processDefinitionKey(processDefinitionKey)
          .messageEventSubscriptionName("msg") // 监听msg message的东西，在本例里是一个intermediate message catch event
          .list();
      for(Execution execution : executions) {
        runtimeService.messageEventReceived("msg", execution.getId());
      }
      
    }

}
