/**
   Copyright 2010, BigDataCraft.Com Ltd.
   David Gruzman

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.Ope
*/
package dremel.tableton;

import java.util.Map;


public interface TabletIterator {

	/**
	 * Returns the map of the column readers comprised the tablet or its projection
	 * @return
	 */
	public abstract Map<String, ColumnReader> getColumnsMap();

	public byte getFetchLevel();
	/**
	 * This is main method of the iterator. It moves all column reader to the next slice.
	 * It doesn't not mean that all are moved to next, but only the necessary one. Can be stated that
	 * at least one should be moved.
	 * This method implements algorithm from the appendix D in the paper
	 * @return true if there is a slice, and false if iteration is finished.
	 */
	public abstract boolean fetch();
	public SchemaColumnar getSchema();
}