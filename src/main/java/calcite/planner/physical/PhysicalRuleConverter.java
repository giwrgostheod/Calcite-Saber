package calcite.planner.physical;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.util.Pair;

import calcite.utils.SaberSchema;
import uk.ac.imperial.lsds.saber.ITupleSchema;
import uk.ac.imperial.lsds.saber.Query;
import uk.ac.imperial.lsds.saber.QueryApplication;
import uk.ac.imperial.lsds.saber.SystemConf;
import uk.ac.imperial.lsds.saber.Utils;
import uk.ac.imperial.lsds.saber.cql.operators.IAggregateOperator;

public class PhysicalRuleConverter {
	
	RelNode logicalPlan;
	
	public PhysicalRuleConverter (RelNode logicalPlan) {
		
		this.logicalPlan=logicalPlan;
	}
	
	public void execute () {
		
		/* Create a list of logical operators for a given plan*/
		String operators[] = RelOptUtil.toString(logicalPlan).split("\\r?\\n");
						
		List<Pair<String,List<String>>> physicalOperators = new ArrayList<Pair<String,List<String>>>();
		String schema = "",table = "",temp,operator,logicalOperator,operands;
		int whiteSpaces; /* whitespaces from the begging of the logical rule will be used for nested joins*/
		for (int counter=operators.length - 1; counter >= 0;counter--){
			
			operator = operators[counter];
			List <String> args = new ArrayList<String>(); 
			//System.out.println("Converting operator : "+ operator);
			whiteSpaces = 0;
			
			if ((operator).contains("LogicalTableScan")){
				temp = operator.substring(0,operator.indexOf("L"));
				whiteSpaces = temp.length();
				
				logicalOperator = (operator.substring(0,operator.indexOf("("))).trim();
				temp = operator.substring(operator.indexOf('[')+2,operator.indexOf(']'));
				String temps[] = temp.split(", ");
				schema = temps[0];
				table = temps[1];
            			args.add("--schema");
            			args.add(schema);
            			args.add("--table");
            			args.add(table);
			} else{
				temp = operator.substring(0,operator.indexOf("L"));
				whiteSpaces = temp.length();
				
				logicalOperator = (operator.substring(0,operator.indexOf("("))).trim();
				operands = operator.substring(operator.indexOf('('));
				args.add("--operands");
				args.add(operands);
			}
			args.add("--whitespaces");
			args.add(Integer.toString(whiteSpaces));
			physicalOperators.add(new Pair<String, List<String>>(logicalOperator,args));
			//System.out.println("WhiteSpaces = "+ whiteSpaces);
		}
		
		System.out.println("---------------------------------------------");
	    	/* Transformation from relational operators to physical => */ 
		
		/* Setting up the input schema and the input data of Saber */
		SaberSchema s = new SaberSchema(4);
		ITupleSchema orders = s.createTable(); //column references have +1 value !!
		orders.setAttributeName(1, "orderid");
		orders.setAttributeName(2, "productid");
		orders.setAttributeName(3, "units");
		orders.setAttributeName(4, "customerid");

		Pair<byte [],ByteBuffer> mockData = s.fillTable(orders);
		byte [] data = mockData.left;
		ByteBuffer b = mockData.right; 

		SaberSchema s1 = new SaberSchema(2);
		ITupleSchema products = s1.createTable(); //column references have +1 value !!
		products.setAttributeName(1, "productid");
		products.setAttributeName(2, "description");	    

		Pair<byte [],ByteBuffer> mockData1 = s1.fillTable(products);
		byte [] data1 = mockData1.left;
		ByteBuffer b1 = mockData1.right; 
    
    		/* Map  used for building  the query. It matches calcite's schemas with saber's.*/
		Map<String, Pair<ITupleSchema,byte []>> tableMap = new HashMap<String, Pair <ITupleSchema,byte []>>();
		tableMap.put("s.orders", new Pair <ITupleSchema,byte []>(orders,data));
		tableMap.put("s.products", new Pair <ITupleSchema,byte []>(products,data1));
		
		/*  Creating a single chain of queries. For complex queries that use JOIN
		 *  we have to create multiple chains and join them. */	    
		RuleAssembler operation;
		SaberRule rule;
		Query query = null;
		ITupleSchema outputSchema = null;
		Set<Query> queries = new HashSet<Query>();
		int queryId = 0;
		long timestampReference = System.nanoTime();
		List <SaberRule> aggregates = new ArrayList <SaberRule>();
		
		
		Map<Integer, List <ChainOfRules>> chainMap = new HashMap<Integer, List <ChainOfRules>>();
		List <ChainOfRules> chains = new ArrayList <ChainOfRules>();
		ChainOfRules chain = null;
		for(Pair<String,List<String>> po : physicalOperators){
			po.right.add("--queryId");
			po.right.add(Integer.toString(queryId));
			po.right.add("--timestampReference");
			po.right.add(Long.toString(timestampReference));

			if (po.left.equals("LogicalTableScan")){
				String tableKey = po.right.get(po.right.indexOf("--schema") +1) + 
						"." + po.right.get(po.right.indexOf("--table") +1);
				Pair<ITupleSchema,byte []> pair = tableMap.get(tableKey);
				operation = new RuleAssembler(po.left, po.right, pair.left);	    
			    	rule = operation.construct();
			    	query = rule.getQuery();
			    	outputSchema = rule.getOutputSchema();			    
				queryId--;

				if (!(chain == null)){
					chains.add(chain);
				}
				int wS = Integer.parseInt(po.right.get( po.right.indexOf("--whitespaces") +1));
				chain = new ChainOfRules(wS,query,outputSchema,pair.right,false);
			} else
			if (po.left.equals("LogicalJoin")) {
				
				/*
				 * Not implemented yet;
				 * */
			    operation = new RuleAssembler(po.left, po.right, null);
			    rule = operation.construct();
			    
			    
			} else					
			if (query==null) {
			    ITupleSchema inputSchema = outputSchema;
			    operation = new RuleAssembler(po.left, po.right, inputSchema);
			    rule = operation.construct();
			    query = rule.getQuery();
			    outputSchema = rule.getOutputSchema();
			    queries.add(query);
			    
			    int wS = Integer.parseInt(po.right.get( po.right.indexOf("--whitespaces") +1));
			    chain.addRule(wS, query, outputSchema);
			} else {
			    operation = new RuleAssembler(po.left, po.right, outputSchema);	    
			    rule = operation.construct();
			    Query query1 = rule.getQuery();
			    outputSchema = rule.getOutputSchema();
			    query.connectTo(query1);
			    queries.add(query1);
			    query = query1; //keep the last query to build the chain
			    
			    int wS = Integer.parseInt(po.right.get( po.right.indexOf("--whitespaces") +1));
			    chain.addRule(wS, query, outputSchema);
			}
			
			if (po.left.equals("LogicalAggregate")){
			    //aggregates.add(rule);
			}
			queryId++;
		    	System.out.println("OutputSchema : " + outputSchema.getSchema());
		    if (!(po.left.equals("LogicalTableScan"))){
		    	System.out.println("Query id : "+ query.getName());
		    }
		    System.out.println();
		}
		chains.add(chain);
				
		QueryApplication application = new QueryApplication(queries);		
		application.setup();
		
		/* The path is query -> dispatcher -> handler -> aggregator */
		/* I am not sure of this : */
		for ( SaberRule agg : aggregates){
			if (SystemConf.CPU)
				agg.getQuery().setAggregateOperator((IAggregateOperator) agg.getCpuCode());
			else
				agg.getQuery().setAggregateOperator((IAggregateOperator) agg.getGpuCode());
		}
		
		/* Execute the query. */
		SystemConf.LATENCY_ON = false;		
		if (SystemConf.LATENCY_ON) {
			long systemTimestamp = (System.nanoTime() - timestampReference) / 1000L; /* usec */
			long packedTimestamp = Utils.pack(systemTimestamp, b.getLong(0));
			b.putLong(0, packedTimestamp);
		}
		
		try {
			while (true) {
				for (ChainOfRules c : chains){
					if(c.getFlag() == false) {
						application.processData (c.getData());
					} else {
						//execute join =>
						//application.processFirstStream  (data1);
						//application.processSecondStream (data2);
					}
					
				}
				if (SystemConf.LATENCY_ON)
					b.putLong(0, Utils.pack((long) ((System.nanoTime() - timestampReference) / 1000L), 1L));
			}
		} catch (Exception e) { 
			e.printStackTrace(); 
			System.exit(1);
		}		
	    
	}

	
}
