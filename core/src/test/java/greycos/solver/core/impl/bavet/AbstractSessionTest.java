package greycos.solver.core.impl.bavet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.stream.Stream;

import greycos.solver.core.impl.bavet.common.AbstractRootNode;
import greycos.solver.core.impl.bavet.common.AbstractRootNode.LifecycleOperation;

import org.junit.jupiter.api.Test;

class AbstractSessionTest {

  @Test
  @SuppressWarnings("unchecked")
  void activationPruningInvalidatesAllLastClassCaches() {
    var network = mock(AbstractBavetNodeNetwork.class);
    AbstractRootNode<Object> insertNode = mock(AbstractRootNode.class);
    AbstractRootNode<Object> updateNode = mock(AbstractRootNode.class);
    AbstractRootNode<Object> retractNode = mock(AbstractRootNode.class);
    AbstractRootNode<Object> inactiveNode = mock(AbstractRootNode.class);
    when(network.getRootNodesAcceptingType(String.class))
        .thenAnswer(ignored -> Stream.of(insertNode, updateNode, retractNode, inactiveNode));
    when(insertNode.supports(LifecycleOperation.INSERT)).thenReturn(true);
    when(updateNode.supports(LifecycleOperation.UPDATE)).thenReturn(true);
    when(retractNode.supports(LifecycleOperation.RETRACT)).thenReturn(true);
    when(inactiveNode.supports(any())).thenReturn(true);
    when(network.isActivationCheckComplete()).thenReturn(true);
    when(network.getActiveNodes()).thenReturn(Set.of(insertNode, updateNode, retractNode));
    var session = new AbstractSession<>(network) {};

    // Shadow initialization can update or retract facts before the first settle.
    session.insert("before");
    session.update("before");
    session.retract("before");
    verify(inactiveNode).insert("before");
    verify(inactiveNode).update("before");
    verify(inactiveNode).retract("before");
    session.settle();
    clearInvocations(insertNode, updateNode, retractNode, inactiveNode);

    // All three operation caches contain this same class before pruning.
    for (var i = 0; i < 2; i++) {
      session.insert("after");
      session.update("after");
      session.retract("after");
    }
    verify(insertNode, times(2)).insert("after");
    verify(updateNode, times(2)).update("after");
    verify(retractNode, times(2)).retract("after");
    verify(insertNode, never()).update(any());
    verify(insertNode, never()).retract(any());
    verify(updateNode, never()).insert(any());
    verify(updateNode, never()).retract(any());
    verify(retractNode, never()).insert(any());
    verify(retractNode, never()).update(any());
    verifyNoInteractions(inactiveNode);
  }

  @Test
  @SuppressWarnings("unchecked")
  void lastClassCachesFollowEachOperationIndependently() {
    var network = mock(AbstractBavetNodeNetwork.class);
    AbstractRootNode<Object> stringNode = mock(AbstractRootNode.class);
    AbstractRootNode<Object> integerNode = mock(AbstractRootNode.class);
    when(network.getRootNodesAcceptingType(String.class))
        .thenAnswer(ignored -> Stream.of(stringNode));
    when(network.getRootNodesAcceptingType(Integer.class))
        .thenAnswer(ignored -> Stream.of(integerNode));
    when(stringNode.supports(any())).thenReturn(true);
    when(integerNode.supports(any())).thenReturn(true);
    var session = new AbstractSession<>(network) {};

    session.insert("a");
    session.update(1);
    session.retract("b");
    session.insert(2);
    session.update("c");
    session.retract(3);
    session.insert("d");
    session.update(4);
    session.retract("e");

    verify(stringNode).insert("a");
    verify(stringNode).insert("d");
    verify(stringNode).update("c");
    verify(stringNode).retract("b");
    verify(stringNode).retract("e");
    verify(integerNode).insert(2);
    verify(integerNode).update(1);
    verify(integerNode).update(4);
    verify(integerNode).retract(3);
  }
}
