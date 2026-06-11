package com.github.mauricioaniche.ck;

import com.github.mauricioaniche.ck.util.FileUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Runner {

	private static String topLevelKey(String className) {
			int idx = className.indexOf('$');
			return (idx >= 0) ? className.substring(0, idx) : className;
	}

	public static void main(String[] args) throws IOException {

		if (args == null || args.length < 1) {
			System.out.println("Usage java -jar ck.jar <path to project> <use Jars=true|false> <max files per partition, 0=automatic selection> <print variables and fields metrics? True|False> <path to save the output files>");
			System.exit(1);
		}

		String path = args[0];

		// use jars?
		boolean useJars = false;
		if(args.length >= 2)
			useJars = Boolean.parseBoolean(args[1]);

		// number of files per partition?
		int maxAtOnce = 0;
		if(args.length >= 3)
			maxAtOnce = Integer.parseInt(args[2]);

		// variables and field results?
		boolean variablesAndFields = true;
		if(args.length >= 4)
			variablesAndFields = Boolean.parseBoolean(args[3]);
		
		// path where the output csv files will be exported
		String outputDir = "";
		if(args.length >= 5)
			outputDir = args[4];

    // load possible additional ignored directories
    //noinspection ManualArrayToCollectionCopy
    for (int i = 5; i < args.length; i++) {
      FileUtils.IGNORED_DIRECTORIES.add(args[i]);
    }

		ResultWriter writer = new ResultWriter(outputDir + "class.csv", outputDir + "method.csv", outputDir + "variable.csv", outputDir + "field.csv", variablesAndFields);
		
		Map<String, CKClassResult> results = new HashMap<>();

		final long[] repoLoc = {0};
		final long[] repoCommentLines = {0};
		
		new CK(useJars, maxAtOnce, variablesAndFields).calculate(path, new CKNotifier() {
			@Override
			public void notify(CKClassResult result) {
				
				// Store the metrics values from each component of the project in a HashMap
				results.put(result.getClassName(), result);
				
			}

			@Override
			public void notifyRepoSummary(long loc, long commentLines) {
				repoLoc[0] = loc;
				repoCommentLines[0] = commentLines;
			}

			@Override
			public void notifyError(String sourceFilePath, Exception e) {
				System.err.println("Error in " + sourceFilePath);
				e.printStackTrace(System.err);
			}
		});

		Map<String, CKClassResult> aggregated = new HashMap<>();

		for (CKClassResult r : results.values()) {
			if (!r.getClassName().contains("$")) {
				aggregated.put(r.getClassName(), r); // key is itself
			}
		}

		// 2) fold all (including top-level if you want) into the top-level
		for (CKClassResult r : results.values()) {
			String key = topLevelKey(r.getClassName());
			CKClassResult acc = aggregated.get(key);
			if (acc == null) continue; // no top-level found (rare), skip

			if (acc == r) continue;

			acc.setWmc(acc.getWmc() + r.getWmc());
			acc.setRfc(acc.getRfc() + r.getRfc());
			acc.setFanin(acc.getFanin() + r.getFanin());
			acc.setFanout(acc.getFanout() + r.getFanout());

			acc.setCbo(Math.max(acc.getCbo(), r.getCbo()));
			acc.setDit(Math.max(acc.getDit(), r.getDit()));
			acc.setLcom(Math.max(acc.getLcom(), r.getLcom()));
		}
		
		// Write the metrics value of each component in the csv files
		for(Map.Entry<String, CKClassResult> entry : aggregated.entrySet()){
			writer.printResult(entry.getValue());
		}
		
		writer.flushAndClose();
		try (java.io.PrintWriter pw = new java.io.PrintWriter(outputDir + "repo.csv")) {
			pw.println("loc,commentLines");
			pw.println(repoLoc[0] + "," + repoCommentLines[0]);
		}
		System.out.println("Metrics extracted!!!");
	}
}
