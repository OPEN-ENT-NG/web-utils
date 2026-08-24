package fr.wseduc.webutils;

import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServerPathConfinementTest {

	private static String root() {
		return Paths.get("srv", "module", "public").toAbsolutePath().normalize().toString();
	}

	@Test
	public void allowsFileDirectlyUnderRoot() {
		final String root = root();
		final String candidate = root + File.separator + "img" + File.separator + "logo.png";
		assertTrue("a file under public must be served", Server.pathIsUnder(root, candidate));
	}

	@Test
	public void allowsRootItself() {
		final String root = root();
		assertTrue("the root itself is under the root", Server.pathIsUnder(root, root));
	}

	@Test
	public void rejectsRelativeTraversalEscapingRoot() {
		final String root = root();
		final String candidate = root + File.separator + ".." + File.separator + ".." + File.separator + "etc" + File.separator + "passwd";
		assertFalse("../ traversal escaping public must be rejected", Server.pathIsUnder(root, candidate));
	}

	@Test
	public void rejectsAbsolutePathOutsideRoot() {
		final String root = root();
		final Path outside = Paths.get(root).getParent().getParent().resolve("etc").resolve("passwd");
		assertFalse("an absolute path outside public must be rejected",
				Server.pathIsUnder(root, outside.toString()));
	}

	@Test
	public void rejectsAbsolutePathWithTraversalEscapingRoot() {
		final String root = root();
		final String candidate = root + File.separator + ".." + File.separator + ".." + File.separator
				+ ".." + File.separator + "etc" + File.separator + "passwd";
		assertFalse("absolute path with .. escaping public must be rejected",
				Server.pathIsUnder(root, candidate));
	}

	@Test
	public void rejectsNullArguments() {
		assertFalse(Server.pathIsUnder(null, "/x"));
		assertFalse(Server.pathIsUnder("/x", null));
		assertFalse(Server.pathIsUnder(null, null));
	}
}
