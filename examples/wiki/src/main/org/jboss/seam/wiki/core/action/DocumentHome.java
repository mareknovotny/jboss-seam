package org.jboss.seam.wiki.core.action;

import org.jboss.seam.annotations.*;
import org.jboss.seam.wiki.core.model.*;
import org.jboss.seam.wiki.core.engine.WikiLinkResolver;
import org.jboss.seam.Component;
import org.jboss.seam.ScopeType;
import org.jboss.seam.core.Events;
import org.jboss.seam.contexts.Contexts;

@Name("documentHome")
@Scope(ScopeType.CONVERSATION)
public class DocumentHome extends NodeHome<Document> {

    /* -------------------------- Context Wiring ------------------------------ */

    @In(required = false) private Node selectedHistoricalNode;

    /* -------------------------- Internal State ------------------------------ */

    private Document historicalCopy;
    @In("#{docEditorPreferences.properties['minorRevisionEnabled']}")
    private boolean minorRevision;
    private String formContent;
    private boolean enabledPreview = false;

    /* -------------------------- Basic Overrides ------------------------------ */

    @Override
    public void create() {
        super.create();

        // Rollback to historical revision?
        if (selectedHistoricalNode != null) getInstance().rollback(selectedHistoricalNode);

        // Make a copy
        historicalCopy = new Document(getInstance());

        // Wiki text parser needs it
        Contexts.getConversationContext().set("currentDocument", getInstance());
        Contexts.getConversationContext().set("currentDirectory", getParentDirectory());
    }

    /* -------------------------- Custom CUD ------------------------------ */

    protected boolean beforePersist() {
        // Sync document content
        syncFormToInstance(getParentDirectory());

        // Make a copy
        historicalCopy = new Document(getInstance());

        return true;
    }

    protected boolean beforeUpdate() {

        // Sync document content
        syncFormToInstance(getParentDirectory());

        // Write history log and prepare a new copy for further modification
        if (!isMinorRevision()) {
            historicalCopy.setId(getInstance().getId());
            getNodeDAO().persistHistoricalNode(historicalCopy);
            getInstance().incrementRevision();
            // New historical copy in conversation
            historicalCopy = new Document(getInstance());
        }

        return true;
    }

    protected boolean beforeRemove() {

        // Delete all history nodes
        getNodeDAO().removeHistoricalNodes(getInstance());

        // Null out default document
        removeAsDefaultDocument(getParentDirectory());

        return true;
    }

    protected void afterNodeMoved(Directory oldParent, Directory newParent) {
        // Update view
        syncFormToInstance(oldParent); // Resolve existing links in old directory
        syncInstanceToForm(newParent); // Now update the form, effectively re-rendering the links
        Contexts.getConversationContext().set("currentDirectory", newParent);
    }

    /* -------------------------- Internal Methods ------------------------------ */


    private void syncFormToInstance(Directory dir) {
        WikiLinkResolver wikiLinkResolver = (WikiLinkResolver)Component.getInstance("wikiLinkResolver");
        getInstance().setContent(
            wikiLinkResolver.convertToWikiProtocol(dir.getAreaNumber(), formContent)
        );
    }

    private void syncInstanceToForm(Directory dir) {
        WikiLinkResolver wikiLinkResolver = (WikiLinkResolver)Component.getInstance("wikiLinkResolver");
        formContent = wikiLinkResolver.convertFromWikiProtocol(dir.getAreaNumber(), getInstance().getContent());
    }

    /* -------------------------- Public Features ------------------------------ */

    public String getFormContent() {
        // Load the document content and resolve links
        if (formContent == null) syncInstanceToForm(getParentDirectory());
        return formContent;
    }

    public void setFormContent(String formContent) {
        this.formContent = formContent;
    }

    public boolean isMinorRevision() { return minorRevision; }
    public void setMinorRevision(boolean minorRevision) { this.minorRevision = minorRevision; }

    public boolean isEnabledPreview() {
        return enabledPreview;
    }

    public void setEnabledPreview(boolean enabledPreview) {
        this.enabledPreview = enabledPreview;
        syncFormToInstance(getParentDirectory());
        refreshMenuItems();
    }

    public void setShowPluginPrefs(boolean showPluginPrefs) {
        Contexts.getConversationContext().set("showPluginPreferences", showPluginPrefs);

    }

    public boolean isShowPluginPrefs() {
        Boolean showPluginPrefs = (Boolean)Contexts.getConversationContext().get("showPluginPreferences");
        return showPluginPrefs != null ? showPluginPrefs : false;
    }
}
