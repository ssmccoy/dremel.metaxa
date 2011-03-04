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
package dremel.server.impl;

import dremel.compiler.impl.Compiler;

/**
 * @author Constantine Peresypkin
 *
 * Wiring for the Compiler interface
 * It's here just for future purposes
 * There are no other Compilers in design doc, but who knows...
 * 
 */
public aspect CompilerWiring {
	
	private Compiler compiler;
	
	pointcut compilerCreation(INeedsCompilerImpl object) :
		execution(INeedsCompilerImpl+.new(..))
		&& this(object);

	after(INeedsCompilerImpl object) 
	returning : compilerCreation(object) {
		compiler = new Compiler();
		object.setCompiler(compiler);
	}
}

