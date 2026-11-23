import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

public final class SquidpyWorkflowUnitTests135 {
    private SquidpyWorkflowUnitTests135() { }
    public static void main(String[] args) {
        var request=LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass("org.jlinalg.cli.SquidpyWorkflowContractsTest")).build();
        var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();launcher.registerTestExecutionListeners(listener);launcher.execute(request);
        var summary=listener.getSummary();var output=new PrintWriter(System.out,true);summary.printTo(output);summary.printFailuresTo(output);
        if(summary.getTestsFoundCount()!=13||summary.getTestsSucceededCount()!=13||summary.getTestsFailedCount()!=0||summary.getTestsSkippedCount()!=0||summary.getContainersFailedCount()!=0)
            throw new IllegalStateException("Expected thirteen actual workflow contract/provenance controls");
    }
}
