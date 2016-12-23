package calcite.planner.logical.rules;

import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.rel.core.Filter;
import org.apache.calcite.rel.core.RelFactories;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexUtil;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.tools.RelBuilderFactory;

/**
 * Planner rule that pushes
 * a {@link org.apache.calcite.rel.logical.LogicalFilter}
 * past a {@link org.apache.calcite.rel.logical.LogicalFilter}.
 */
public class FilterPushThroughFilter extends RelOptRule {

  public static final FilterPushThroughFilter INSTANCE =
      new FilterPushThroughFilter(Filter.class, Filter.class,
          RelFactories.LOGICAL_BUILDER);


  //~ Constructors -----------------------------------------------------------

  public FilterPushThroughFilter(
      Class<? extends Filter> filterClass,
      Class<? extends Filter> secFilterClass,      
      RelBuilderFactory relBuilderFactory) {
    super(
        operand(filterClass,
            operand(secFilterClass, any())),
        relBuilderFactory, null);
  }

  @Deprecated // to be removed before 2.0
  public FilterPushThroughFilter(
      Class<? extends Filter> filterClass,
      RelFactories.FilterFactory filterFactory,
      Class<? extends Filter> secFilterClass,
      RelFactories.FilterFactory secFilterFactory) {
    this(filterClass, secFilterClass, RelBuilder.proto(filterFactory, secFilterFactory));
  }

  //~ Methods ----------------------------------------------------------------

  public void onMatch(RelOptRuleCall call) {
    final Filter filter = call.rel(0);
    final Filter secFilter = call.rel(1);

    if (RexUtil.containsCorrelation(filter.getCondition())) {
      // If there is a correlation condition anywhere in the filter, don't
      // push this filter past project since in some cases it can prevent a
      // Correlate from being de-correlated.
      return;
    }
    
    if (RexUtil.containsCorrelation(secFilter.getCondition())) {
        // If there is a correlation condition anywhere in the filter, don't
        // push this filter past project since in some cases it can prevent a
        // Correlate from being de-correlated.
        return;
    }

    final LogicalFilter newFilter = LogicalFilter.create(secFilter.getInput(), filter.getCondition());
    final LogicalFilter newSecFilter = LogicalFilter.create(newFilter, secFilter.getCondition());
    call.transformTo(newSecFilter);
  }
}
