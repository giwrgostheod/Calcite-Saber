package calcite.planner.common;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Filter;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rex.RexNode;

import calcite.cost.SaberCostBase;
import calcite.cost.SaberCostBase.SaberCostFactory;
import calcite.planner.logical.SaberRel;

public abstract class SaberFilterRelBase extends Filter implements SaberRel {

	protected SaberFilterRelBase(RelOptCluster cluster, RelTraitSet traits,
              RelNode child, RexNode condition) {
		super(cluster, traits, child, condition);
	}
	
	@Override public RelOptCost computeSelfCost(RelOptPlanner planner, RelMetadataQuery mq) {
	      
		double rowCount = mq.getRowCount(this); 
	    double selectivity = mq.getSelectivity(this.getInput(), this.getCondition());
	    double rate = selectivity * ((SaberCostBase) mq.getCumulativeCost(this.getInput())).getRate();
	    double cpuCost = SaberCostBase.Cs * rate;
	    //System.out.println("selectivity:" + selectivity );	    
		double window =  selectivity * ((SaberCostBase) mq.getCumulativeCost(this.getInput())).getWindow();
		window = (window < 1) ? 1 : window; // fix window size in order to be >= 1
		double R = (((SaberCostBase) mq.getCumulativeCost(this.getInput())).getCpu() + cpuCost) / rate;
		SaberCostFactory costFactory = (SaberCostFactory)planner.getCostFactory();
		return costFactory.makeCost(rowCount, cpuCost, 0, rate, 0, window, R);
	}
	
}
