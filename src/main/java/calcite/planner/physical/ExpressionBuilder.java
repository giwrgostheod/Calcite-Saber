package calcite.planner.physical;

import java.util.ArrayList;
import java.util.List;

import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.util.Pair;

import uk.ac.imperial.lsds.saber.cql.expressions.Expression;
import uk.ac.imperial.lsds.saber.cql.expressions.floats.FloatColumnReference;
import uk.ac.imperial.lsds.saber.cql.expressions.floats.FloatConstant;
import uk.ac.imperial.lsds.saber.cql.expressions.floats.FloatDivision;
import uk.ac.imperial.lsds.saber.cql.expressions.floats.FloatExpression;
import uk.ac.imperial.lsds.saber.cql.expressions.floats.FloatMultiplication;
import uk.ac.imperial.lsds.saber.cql.expressions.ints.IntAddition;
import uk.ac.imperial.lsds.saber.cql.expressions.ints.IntColumnReference;
import uk.ac.imperial.lsds.saber.cql.expressions.ints.IntConstant;
import uk.ac.imperial.lsds.saber.cql.expressions.ints.IntDivision;
import uk.ac.imperial.lsds.saber.cql.expressions.ints.IntExpression;
import uk.ac.imperial.lsds.saber.cql.expressions.ints.IntMultiplication;
import uk.ac.imperial.lsds.saber.cql.expressions.ints.IntSubtraction;

public class ExpressionBuilder {

	RexNode expression;
	
	public ExpressionBuilder(RexNode expression) {
		this.expression = expression;
	}

	public Expression build() {
		Expression saberExpression = getExpression(expression).right;	
		System.out.println(saberExpression.toString());
		return saberExpression;
	}

	private Pair<RexNode, Expression> getExpression(RexNode expression) {
	    if (expression instanceof RexCall) {
			List <Pair<RexNode, Expression>> operands = new ArrayList <Pair<RexNode, Expression>>();
	        for (RexNode operand : ((RexCall) expression).getOperands()) {
	        	operands.add(getExpression(operand));	        	
	        } 	   
	        String operator = ((RexCall) expression).getOperator().toString();
	        if ((operator.equals("+")) || (operator.equals("-")) || 
	        		(operator.equals("*")) || (operator.equals("/")) ){        		        	
	        	return new Pair<RexNode,Expression>(expression, getSimpleExpression(operands, operator));        	
	        } else
	        if (operator.equals("CASE")) {
	        	return null;
	    	} else 
	    	if (operator.equals("CAST")) { 
	        	return null;
	        }
	    	if (operator.equals("FLOOR")) { 
	        	return null;
	        }
	    	if (operator.equals("CEIL")) { 
	        	return null;
	        }
	        return null;
	    } else {  
	    	Expression expr = null;
			if (expression.getKind().toString().equals("LITERAL")) {				
				if (expression.getType().toString().equals("INTEGER"))
					expr = new IntConstant(Integer.parseInt(expression.toString()));
				else  
					expr = new FloatConstant(Float.parseFloat(expression.toString()));
			} else 
			if (expression.getKind().toString().equals("INPUT_REF")){
				int column = Integer.parseInt(expression.toString().replace("$", ""));				
				if (expression.getType().toString().equals("INTEGER"))
					expr = new IntColumnReference (column);
				else 
					expr = new FloatColumnReference (column);
			} 			
	    	return new Pair<RexNode,Expression>(expression,expr);
	    }
	}

	private Expression getSimpleExpression(List<Pair<RexNode, Expression>> operands, String operator) {
		Expression simpleExpression = null;
		Pair<RexNode, Expression> pair1 = operands.get(0);
		Pair<RexNode, Expression> pair2 = operands.get(1);
    	if ( (pair1.left.getType().toString().equals("INTEGER")) && 
    			(pair2.left.getType().toString().equals("INTEGER"))){
    		if (operator.equals("+")) {    			
    			return new IntAddition((IntExpression)pair1.right,(IntExpression) pair2.right);
    		} else
        	if (operator.equals("-")) {    			
        		return new IntSubtraction((IntExpression)pair1.right,(IntExpression) pair2.right);
        	} else
    		if (operator.equals("*")) {    			
    			return new IntMultiplication((IntExpression)pair1.right,(IntExpression) pair2.right);
    		} else
    		if (operator.equals("/")) {    			
    			return new IntDivision((IntExpression)pair1.right,(IntExpression) pair2.right);
    		}    		
    	} else {
    		if (operator.equals("+")) {    			
				System.err.println("error: not implemented yet");
				System.exit(1);
				return null;
    			//return new FloatAddition((FloatExpression)pair1.right,(FloatExpression) pair2.right);    			
    		} else
        	if (operator.equals("-")) {  
				System.err.println("error: not implemented yet");
				System.exit(1);
				return null;
        		//return new FloatSubtraction((FloatExpression)pair1.right,(FloatExpression) pair2.right);
        	} else
    		if (operator.equals("*")) {    			
    			return new FloatMultiplication((FloatExpression)pair1.right,(FloatExpression) pair2.right);
    		} else
    		if (operator.equals("/")) {    			
    			return new FloatDivision((FloatExpression)pair1.right,(FloatExpression) pair2.right);
    		}  
    	}    
		System.err.println("error: not implemented yet");
		System.exit(1);
		return simpleExpression;
	}

	
	
}
