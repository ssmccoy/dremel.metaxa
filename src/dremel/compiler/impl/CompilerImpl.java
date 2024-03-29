package dremel.compiler.impl;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.commons.compiler.IScriptEvaluator;

import dremel.compiler.Expression.Function;
import dremel.compiler.Expression.Node;
import dremel.compiler.Expression.Symbol;
import dremel.compiler.Query;
import dremel.compiler.parser.AstNode;
import dremel.compiler.parser.impl.BqlParser;
import dremel.dataset.SchemaTree;
import dremel.dataset.Slice;
import dremel.dataset.Table;
import dremel.executor.Executor;

/**
 * @author nhsan
 * 
 */
public class CompilerImpl implements dremel.compiler.Compiler {

	/*
	 * (non-Javadoc)
	 * 
	 * @see dremel.compiler.Compiler#parse(dremel.parser.AstNode)
	 */
	@Override
	public Query parse(AstNode root) {
		Query query = new DefaultQuery();
		parseSelectStatement(root, query);
		return query;
	}

	public static String getID(AstNode node) {
		StringBuilder ret = new StringBuilder();
		assert (node.getType() == BqlParser.N_ID);
		assert (node.getChildCount() >= 1);
		AstNode node2 = (AstNode) node.getChild(0);
		assert (node2.getType() == BqlParser.N_NAME);
		assert (node2.getChildCount() == 1);
		ret.append(node2.getChild(0).toString());

		for (int i = 1; i < node.getChildCount(); i++) {
			node2 = (AstNode) node.getChild(i);
			assert (node2.getType() == BqlParser.N_NAME);
			assert (node2.getChildCount() == 1);
			ret.append(".");
			ret.append(node2.getChild(0).toString());
		}
		return ret.toString();
	}

	void parseSelectStatement(AstNode node, Query query) {
		assert (node.getType() == BqlParser.N_SELECT_STATEMENT);
		int count = node.getChildCount();
		assert (count >= 2);
		parseFromClause((AstNode) node.getChild(0), query);
		parseSelectClause((AstNode) node.getChild(1), query);
		parseWhereClause((AstNode) node.getChild(2), query);
	}

	void parseFromClause(AstNode node, Query query) {
		assert (node.getType() == BqlParser.N_FROM);
		int count = node.getChildCount();
		assert (count > 0);
		for (int i = 0; i < count; i++) {
			AstNode node2 = (AstNode) node.getChild(i);
			if (node2.getType() == BqlParser.N_TABLE_NAME) {

				assert (node2.getChildCount() == 1);
				AstNode node3 = (AstNode) node2.getChild(0);
				List<Table> tables = query.getTables();
				tables.add(new dremel.dataset.impl.TableImpl(node3.getText()));
			} else if (node2.getType() == BqlParser.N_SELECT_STATEMENT) {
				List<dremel.compiler.Query> queries = query.getSubQueries();
				queries.add(parse(node2));
			} else
				assert (false);
		}
	}

	void parseSelectClause(AstNode node, Query query) {
		assert (node.getType() == BqlParser.N_SELECT);
		int count = node.getChildCount();
		assert (count > 0);
		for (int i = 0; i < count; i++) {
			parseCreateColumn((AstNode) node.getChild(i), query);
		}
	}

	void parseWhereClause(AstNode node, Query query) {
		if (node == null) {
			((DefaultQuery) query).setFilter(null);
			return;
		}
		assert (node.getType() == BqlParser.N_WHERE);
		int count = node.getChildCount();
		assert (count == 1);
		Node filterNode = Expression.buildNode((AstNode) node.getChild(0), query);
		dremel.compiler.impl.Expression f = new dremel.compiler.impl.Expression();
		f.setRoot(filterNode);
		((DefaultQuery) query).setFilter(f);
	}

	void parseCreateColumn(AstNode node, Query query) {
		StringBuffer alias = new StringBuffer();
		StringBuffer within = new StringBuffer();
		boolean isWithinRecord = false;
		boolean hasAlias = false;
		assert (node.getType() == BqlParser.N_CREATE_COLUMN);
		int count = node.getChildCount();
		assert ((count >= 1) && (count <= 3));
		if (count == 3) {
			parseColumnAlias((AstNode) node.getChild(1), alias);
			hasAlias = true;
			if (node.getChild(2).getType() == BqlParser.N_WITHIN)
				parseWithinClause((AstNode) node.getChild(2), within);
			else if (node.getChild(2).getType() == BqlParser.N_WITHIN_RECORD)
				isWithinRecord = parseWithinRecordClause((AstNode) node.getChild(2), query);
		} else if (count == 2) {
			if (node.getChild(1).getType() == BqlParser.N_ALIAS) {
				hasAlias = true;
				parseColumnAlias((AstNode) node.getChild(1), alias);
			} else if (node.getChild(1).getType() == BqlParser.N_WITHIN)
				parseWithinClause((AstNode) node.getChild(1), within);
			else if (node.getChild(1).getType() == BqlParser.N_WITHIN_RECORD)
				isWithinRecord = parseWithinRecordClause((AstNode) node.getChild(1), query);
			else {
				assert (false);
			}
		} else if (node.getChild(0).getChildCount() == 1 && node.getChild(0).getChild(0).getType() == BqlParser.N_ID) {
			alias.append(getID((AstNode) node.getChild(0).getChild(0)));
		}

		assert (node.getChild(0).getType() == BqlParser.N_EXPRESSION);
		Node root = Expression.buildNode((AstNode) node.getChild(0), query);
		dremel.compiler.impl.Expression expression = new dremel.compiler.impl.Expression();

		if (isWithinRecord)
			expression.setScope("RECORD");
		else if (within.toString().length() > 0)
			expression.setScope(within.toString());
		else
			expression.setScope(null);

		if (hasAlias) {
			String aStr = alias.toString();
			expression.setAlias(aStr);
			assert (!query.getSymbolTable().containsKey(aStr));
			Symbol symbol = new dremel.compiler.impl.Expression.Symbol(aStr, query);
			symbol.setReference(expression);
			query.getSymbolTable().put(aStr, symbol);
		} else if (alias.toString().length() > 0) {
			expression.setAlias(alias.toString());
		}

		expression.setRoot(root);
		query.getSelectExpressions().add(expression);
	};

	private boolean parseWithinRecordClause(AstNode node, Query query) {
		assert (node.getType() == BqlParser.N_WITHIN_RECORD);
		return true;
	}

	private void parseWithinClause(AstNode node, StringBuffer within) {
		assert (node.getType() == BqlParser.N_WITHIN);
		int count = node.getChildCount();
		assert ((count == 1));
		within.append(getID((AstNode) node.getChild(0)));
	}

	private void parseColumnAlias(AstNode node, StringBuffer alias) {
		assert (node.getType() == BqlParser.N_ALIAS);
		int count = node.getChildCount();
		assert ((count == 1));
		node = (AstNode) node.getChild(0);
		assert (node.getType() == BqlParser.N_NAME);
		assert (node.getChildCount() == 1);
		alias.append(node.getChild(0).getText());
	}

	/**
	 * calMaxLevel is a recursive function to calculate repetition level of
	 * fields in schema
	 * 
	 * @param desc
	 * @param level
	 * @param maxLevels
	 */
	private void calMaxLevel(SchemaTree desc, int level, Map<SchemaTree, Integer> maxLevels) {
		List<SchemaTree> fs = desc.getFieldsList();
		for (int i = 0; i < fs.size(); i++) {
			SchemaTree d = fs.get(i);
			if (d.isRepeated()) {

				if (d.isRecord()) {
					calMaxLevel(d, level + 1, maxLevels);
					maxLevels.put(d, level + 1);
				} else {
					maxLevels.put(d, level + 1);
				}
			} else {
				if (d.isRecord()) {
					calMaxLevel(d, level, maxLevels);
					maxLevels.put(d, level);
				} else {
					maxLevels.put(d, level);
				}
			}
		}
	}

	/**
	 * calculate repetition level of an expression= max repetition level of
	 * fields used in expression
	 * 
	 * @param node
	 * @param level
	 * @param maxLevels
	 * @return
	 */
	int getRLevel(Node node, int level, Map<SchemaTree, Integer> maxLevels) {
		if (node instanceof Symbol) {
			Symbol symbol = (Symbol) node;
			Object o = symbol.getReference();
			if (o instanceof SchemaTree) {
				int l = maxLevels.get(o);
				if (l > level)
					level = l;
			} else if (o instanceof Expression) {
				Expression exp = (Expression) o;
				int l = exp.getRepetitionLevel();
				if (l > level)
					level = l;
			}
		} else {
			for (int i = 0; i < node.getChildCount(); i++) {
				Node n = node.getChild(i);
				level = getRLevel(n, level, maxLevels);
			}
		}
		return level;
	}

	void getRelatedFields(Node node, List<SchemaTree> fields) {
		if (node instanceof Symbol) {
			Symbol symbol = (Symbol) node;
			Object o = symbol.getReference();
			if (o instanceof SchemaTree) {
				if (!fields.contains(o))
					fields.add((SchemaTree) o);
			} else if (o instanceof Expression) {
				Expression exp = (Expression) o;
				List<SchemaTree> lst = exp.getRelatedFields();
				Iterator<SchemaTree> it = lst.iterator();

				while (it.hasNext()) {
					SchemaTree d = it.next();
					if (!fields.contains(d))
						fields.add(d);
				}
			}
		} else {
			for (int i = 0; i < node.getChildCount(); i++) {
				Node n = node.getChild(i);
				getRelatedFields(n, fields);
			}
		}
	}

	/**
	 * get aggregation function list of an expression
	 * 
	 * @param node
	 * @param aggFuncs
	 */
	void getAggregationFunction(Node node, List<Function> aggFuncs) {
		if (node instanceof Function) {
			Function func = (Function) node;
			if (func.getName().equalsIgnoreCase("count") || func.getName().equalsIgnoreCase("sum"))
				aggFuncs.add(func);
		} else {
			for (int i = 0; i < node.getChildCount(); i++) {
				Node n = node.getChild(i);
				getAggregationFunction(n, aggFuncs);
			}
		}
	}

	/**
	 * resolve within node and return level of within node
	 * 
	 * @param nodeName
	 * @param maxLevels
	 * @return
	 */
	int getWithinLevel(String nodeName, Map<SchemaTree, Integer> maxLevels) {
		if (nodeName == null)
			return -1;
		if (nodeName.equalsIgnoreCase("record"))
			return 0;

		Iterator<SchemaTree> it = maxLevels.keySet().iterator();

		while (it.hasNext()) {
			SchemaTree d = it.next();

			if (d.isRecord()) // within node must be group
			{
				String name = getFieldName(d.getName());
				// System.out.println(name);
				if (name.equalsIgnoreCase(nodeName))
					return maxLevels.get(d);
			}
		}
		return -1;
	}

	String getFieldName(String name) {
		// input schema.Document.*
		// trim prefix: schema.Document.
		int p = name.indexOf('.');
		if (p > 0) {
			p = name.indexOf('.', p + 1);
			if (p > 0) {
				name = name.substring(p + 1);
			}
		}
		return name;
	}

	/*
	 * Validation rules: - check table name: enclose by [] - check field names
	 * against schema (field name in expressions + WITHIN clause) - check alias:
	 * alias ~ expression in SELECT clause, can be used in order by, group by,
	 * and where clause - check WITHIN clause: must contains aggregation
	 * function, field in aggregation function must be child of WITHIN node -
	 * check data type of expression - check WHERE clause: Aggregate functions
	 * cannot be used in the WHERE clause - check GROUPBY clause: Non-aggregated
	 * fields in the SELECT clause must be listed in the GROUP BY clause - check
	 * GROUPBY clause: Fields in the GROUP BY clause must be listed in the
	 * SELECT clause - and more...
	 */

	/*
	 * Analyze rule: - build symbol table: fields + alias - find related field
	 * list (ordered, will be order in slice) - calculate repetition level for
	 * expressions, within nodes, aggregation functions
	 */

	@Override
	public void analyse(Query query) {
		assert (query.getTables().size() == 1);// one table only
		assert (query.getSubQueries().size() == 0);// no sub-queries
		SchemaTree SchemaTree = query.getTables().get(0).getSchema();
		Map<SchemaTree, Integer> maxLevels = new HashMap<SchemaTree, Integer>();

		// bind field+exp to symbols
		calMaxLevel(SchemaTree, 0, maxLevels);
		Iterator<SchemaTree> fIt = maxLevels.keySet().iterator();
		while (fIt.hasNext()) {
			SchemaTree d = fIt.next();
			String name = getFieldName(d.getName());

			Symbol symbol = query.getSymbolTable().get(name.toLowerCase());
			if (symbol != null) {
				if (symbol.getReference() == null) {
					symbol.setReference(d);
				} else {
					assert (symbol.getReference() == d);// no duplicate symbol
				}
			}
		}

		Iterator<Symbol> it = query.getSymbolTable().values().iterator();
		while (it.hasNext()) {
			Symbol symbol = it.next();
			// assert (symbol.getReference() != null);// no symbol without
			// reference
		}

		Iterator eIt = query.getSelectExpressions().iterator();
		while (eIt.hasNext()) {
			Expression exp = (Expression) eIt.next();
			int level = getRLevel(exp.getRoot(), 0, maxLevels);
			exp.setRLevel(level);
			getAggregationFunction(exp.getRoot(), query.getAggregationFunctions());
			int scopeLevel = getWithinLevel(exp.getWithin(), maxLevels);
			exp.setWithinLevel(scopeLevel);
			getRelatedFields(exp.getRoot(), exp.getRelatedFields());
		}

		Expression exp = (Expression) query.getFilter();
		if (exp != null) {
			int level = getRLevel(exp.getRoot(), 0, maxLevels);
			exp.setRLevel(level);
			getAggregationFunction(exp.getRoot(), query.getAggregationFunctions());
		}
	}

	public String generateCode(Query query) {
		Iterator<Symbol> it = query.getSymbolTable().values().iterator();

		int i = 0;
		while (it.hasNext()) {
			Symbol symbol = it.next();
			if (symbol.getReference() instanceof SchemaTree) {
				symbol.setSliceMappingIndex(i++);
			}
		}
		VelocityContext context = new VelocityContext();
		context.put("query", query);

		Template template = null;

		try {
			template = Velocity.getTemplate("src/dremel/executor/executor.vm");
		} catch (Exception e) {
			e.printStackTrace();
		}

		StringWriter sw = new StringWriter();
		template.merge(context, sw);
		return sw.toString();
	}

	public IScriptEvaluator createScript(String code) throws Exception {
		IScriptEvaluator se = CompilerFactoryFactory.getDefaultCompilerFactory().newScriptEvaluator();
		se.setReturnType(void.class);
		se.setDefaultImports(new String[] { "dremel.compiler.*", "dremel.compiler.expression.*" });

		se.setParameters(new String[] { "inSlice", "outSlice", "context1" }, new Class[] { Slice.class, Slice.class, Integer[].class });

		se.cook(code);
		return se;
	}

	@Override
	public Executor compile(Query query) {
		return null;
	}
	
	@Override
	public String compileToScript(Query query) {
		VelocityContext context = new VelocityContext();

		List<SchemaTree> fields = new LinkedList<SchemaTree>();

		try {
			Iterator<Symbol> it = query.getSymbolTable().values().iterator();
			int i = 0;
			while (it.hasNext()) {
				Symbol symbol = it.next();
				if (symbol.getReference() instanceof SchemaTree) {
					symbol.setSliceMappingIndex(i++);
					fields.add((SchemaTree) symbol.getReference());
				}
			}

			//assert (query.getTables().get(0).getSchema() == Document.getSchemaTree());
			//SliceScanner scanner = new SimpleSliceScanner(fields, query.getTables().get(0).getDataDir());

			context.put("query", query);

			Template template = null;

			try {
				template = Velocity.getTemplate("src/dremel/executor/executor.vm");
			} catch (Exception e) {
				e.printStackTrace();
			}

			StringWriter sw = new StringWriter();
			template.merge(context, sw);
			
			//Script script= new MetaxaExecutor.JavaLangScript(sw.toString());
			//Executor executor = new MetaxaExecutor(query, scanner, script);
			//return executor;
			return sw.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

/*	public static void main(String[] args) throws RecognitionException {

		Velocity.init();
		CompilerImpl compiler = new dremel.compiler.impl.CompilerImpl();
		AstNode nodes = Parser.parseBql("SELECT \ndocid, name.language.code,length(name.language.code), links.forward as fwd, links.forward+links.backward FROM [document] WHERE fwd>0;");

		Query query = compiler.parse(nodes);
		compiler.analyse(query);
		Executor executor = compiler.compile(query);
		executor.execute();
	}*/
}
