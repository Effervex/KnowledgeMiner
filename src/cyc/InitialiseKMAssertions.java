/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *    Sam Sarjant - initial API and implementation
 ******************************************************************************/
package cyc;

import io.ResourceAccess;

public class InitialiseKMAssertions {
	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("Please provide a port number.");
			System.exit(0);
		}
		ResourceAccess.newInstance(Integer.parseInt(args[0]));
		try {
			CycConstants.initialiseAssertions(ResourceAccess
					.requestOntologySocket());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
