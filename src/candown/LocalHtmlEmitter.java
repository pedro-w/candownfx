/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package candown;

import org.tautua.markdownpapers.HtmlEmitter;
import org.tautua.markdownpapers.ast.InlineUrl;
import org.tautua.markdownpapers.ast.Link;
import org.tautua.markdownpapers.ast.Resource;

/**
 * Emitter that squashes non-local links.
 * Exactly like org.tautua.markdownpapers.HtmlEmitter but any
 * link not starting with # is suppressed.
 * @author Peter Hull 
 */
public class LocalHtmlEmitter extends HtmlEmitter {

	public LocalHtmlEmitter(Appendable buffer) {
		super(buffer);
	}

	@Override
	public void visit(InlineUrl node) {
		append("<a href=\"");
		final String url = node.getUrl();
		if (url.startsWith("#")) {
			escapeAndAppend(url);
		}
		append("\">");
		escapeAndAppend(url);
		append("</a>");
	}

	@Override
	public void visit(Link node) {
		Resource resource = node.getResource();
		if (resource == null) {
			if (node.isReferenced()) {
				append("[");
				node.childrenAccept(this);
				append("]");
				if (node.getReference() != null) {
					if (node.hasWhitespaceAtMiddle()) {
						append(' ');
					}
					append("[");
					append(node.getReference());
					append("]");
				}
			} else {
				append("<a href=\"\">");
				node.childrenAccept(this);
				append("</a>");
			}
		} else {
			append("<a ");
			final String location = resource.getLocation();
			if (location.startsWith("#")) {
				// local link
				append("href=\"");
				escapeAndAppend(location);
				append("\" ");
			} else {
				append("href=\"#\"");
			}
			if (resource.getHint() != null) {
				append("title=\"");
				escapeAndAppend(resource.getHint());
				append("\" ");
			}
			else {
				append("title=\"hiya\" ");
			}
			append(">");
			node.childrenAccept(this);
			append("</a>");
		}
	}
}
