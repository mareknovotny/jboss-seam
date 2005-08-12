/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.seam;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.logging.Logger;

/**
 * For stateless objects
 * 
 * @author Gavin King
 * @author <a href="mailto:theute@jboss.org">Thomas Heute</a>
 * @version $Revision$
 */
public class StatelessContext implements Context {

	private static final Logger log = Logger.getLogger(StatelessContext.class);

	public StatelessContext() {
		super();
	}

	public Object get(String name) {
		log.info("resolving: " + name);
		try {
			return new InitialContext().lookup(name);
		}
		catch (NamingException ne) {
			return null;
		}
	}

	public void set(String name, Object value) {
		log.info("binding: " + name);
		try {
			new InitialContext().bind(name, value);
		}
		catch (NamingException ne) {
			log.debug("could not bind: " + name, ne);
			throw new IllegalArgumentException("could not bind: " + name, ne);
		}
	}

	public boolean isSet(String name) {
		return get(name)!=null;
	}

}
