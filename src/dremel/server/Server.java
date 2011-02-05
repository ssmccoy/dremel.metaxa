/**
 * Copyright 2010, BigDataCraft.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dremel.server;

import dremel.dataset.ReaderTree;
import dremel.dataset.Stream.Codec;

/**
 * Server is a factory and facade for the almost all the rest of system modules. 
 * <P>
 * The implementation may use initially a piece of java code, probably better named wire, to 
 * instantiate correct implementations. Later on when and if wiring will get complicated a 
 * google guice auto-wiring framework can be brought in.
 * <P>
 * Regarding facade functionality, it is in fact fully encapsulated inside queryImmidiate method. 
 *    
 *
 */
public interface Server {
	ReaderTree queryImmediate(String Query, String schemaFilename, String dataFilename);
	void toFile(ReaderTree readerTree, 
			String destinationDataFilename, 
			Codec destinationCodec, 
			String destinationSchemaFilename);
	void toFile(ReaderTree readerTree, 
			String destinationDataFilename, 
			Codec destinationCodec);
	ReaderTree fromFile(ReaderTree readerTree, 
			String sourceDataFilename, 
			Codec sourceCodec, 
			String sourceSchemaFilename);
    void convertFile(Codec sourceCodec, 
    				String sourceSchemaFilename, 
    				String sourceDataFilename, 
    				Codec destinationCodec, 
    				String destinationSchemaFilename, 
    				String destinationDataFilename);
}
