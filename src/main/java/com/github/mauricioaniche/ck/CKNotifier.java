package com.github.mauricioaniche.ck;

public interface CKNotifier {
	void notify(CKClassResult result);
	default void notifyRepoSummary(long loc, long commentLines) {}
	default void notifyError(String sourceFilePath, Exception e) {}
}
