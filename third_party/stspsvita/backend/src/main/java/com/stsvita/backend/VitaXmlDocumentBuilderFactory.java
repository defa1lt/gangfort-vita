package com.stsvita.backend;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.XmlReader;
import java.io.IOException;
import java.io.Reader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Minimal DOM adapter for the Tiled map files parsed by the original game. */
public final class VitaXmlDocumentBuilderFactory extends DocumentBuilderFactory {
    @Override
    public DocumentBuilder newDocumentBuilder() {
        return new VitaXmlDocumentBuilder();
    }

    @Override public void setAttribute(String name, Object value) { throw new IllegalArgumentException(name); }
    @Override public Object getAttribute(String name) { throw new IllegalArgumentException(name); }
    @Override public void setFeature(String name, boolean value) throws ParserConfigurationException {
        throw new ParserConfigurationException(name);
    }
    @Override public boolean getFeature(String name) throws ParserConfigurationException {
        throw new ParserConfigurationException(name);
    }

    private static final class VitaXmlDocumentBuilder extends DocumentBuilder {
        @Override
        public Document parse(InputSource source) throws SAXException, IOException {
            Reader reader = source.getCharacterStream();
            if (reader == null) throw new SAXException("Tiled XML must use a character stream");
            StringBuilder xml = new StringBuilder();
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) >= 0) xml.append(buffer, 0, count);
            try {
                XmlReader.Element root = new XmlReader().parse(xml.toString());
                return new VitaXmlDocument(root);
            } catch (RuntimeException error) {
                throw new SAXException("Invalid Tiled XML", error);
            }
        }

        @Override public boolean isNamespaceAware() { return false; }
        @Override public boolean isValidating() { return false; }
        @Override public void setEntityResolver(EntityResolver resolver) {}
        @Override public void setErrorHandler(ErrorHandler handler) {}
        @Override public Document newDocument() { return new VitaXmlDocument(null); }
        @Override public DOMImplementation getDOMImplementation() { return null; }
    }

    private abstract static class VitaNode implements Node {
        final String name;
        final VitaXmlDocument document;

        VitaNode(String name, VitaXmlDocument document) {
            this.name = name;
            this.document = document;
        }

        @Override public String getNodeName() { return name; }
        @Override public String getNodeValue() { return null; }
        @Override public void setNodeValue(String value) {}
        @Override public Node getParentNode() { return null; }
        @Override public NodeList getChildNodes() { return EmptyNodeList.INSTANCE; }
        @Override public Node getFirstChild() { return null; }
        @Override public Node getLastChild() { return null; }
        @Override public Node getPreviousSibling() { return null; }
        @Override public Node getNextSibling() { return null; }
        @Override public NamedNodeMap getAttributes() { return null; }
        @Override public Document getOwnerDocument() { return document; }
        @Override public Node insertBefore(Node child, Node reference) { throw unsupported(); }
        @Override public Node replaceChild(Node child, Node old) { throw unsupported(); }
        @Override public Node removeChild(Node child) { throw unsupported(); }
        @Override public Node appendChild(Node child) { throw unsupported(); }
        @Override public boolean hasChildNodes() { return getChildNodes().getLength() != 0; }
        @Override public Node cloneNode(boolean deep) { return this; }
        @Override public void normalize() {}
        @Override public boolean isSupported(String feature, String version) { return false; }
        @Override public String getNamespaceURI() { return null; }
        @Override public String getPrefix() { return null; }
        @Override public void setPrefix(String prefix) { throw unsupported(); }
        @Override public String getLocalName() { return name; }
        @Override public boolean hasAttributes() { return false; }
        @Override public String getBaseURI() { return null; }
        @Override public short compareDocumentPosition(Node other) { return 0; }
        @Override public String getTextContent() { return getNodeValue(); }
        @Override public void setTextContent(String text) { throw unsupported(); }
        @Override public boolean isSameNode(Node other) { return this == other; }
        @Override public String lookupPrefix(String namespace) { return null; }
        @Override public boolean isDefaultNamespace(String namespace) { return false; }
        @Override public String lookupNamespaceURI(String prefix) { return null; }
        @Override public boolean isEqualNode(Node other) { return this == other; }
        @Override public Object getFeature(String feature, String version) { return null; }
        @Override public Object setUserData(String key, Object data, org.w3c.dom.UserDataHandler handler) { return null; }
        @Override public Object getUserData(String key) { return null; }

        final DOMException unsupported() {
            return new DOMException(DOMException.NOT_SUPPORTED_ERR, "read-only Tiled DOM");
        }
    }

    private static final class VitaXmlDocument extends VitaNode implements Document {
        final XmlReader.Element root;

        VitaXmlDocument(XmlReader.Element root) {
            super("#document", null);
            this.root = root;
        }

        @Override public short getNodeType() { return DOCUMENT_NODE; }
        @Override public Document getOwnerDocument() { return null; }
        @Override public Element getDocumentElement() { return root == null ? null : new VitaXmlElement(root, this); }
        @Override public NodeList getElementsByTagName(String name) {
            Array<XmlReader.Element> matches = new Array<>();
            collect(root, name, matches, true);
            return new VitaXmlNodeList(matches, this);
        }
        @Override public DocumentType getDoctype() { return null; }
        @Override public DOMImplementation getImplementation() { return null; }
        @Override public Element createElement(String name) { throw unsupported(); }
        @Override public DocumentFragment createDocumentFragment() { throw unsupported(); }
        @Override public Text createTextNode(String data) { throw unsupported(); }
        @Override public org.w3c.dom.Comment createComment(String data) { throw unsupported(); }
        @Override public org.w3c.dom.CDATASection createCDATASection(String data) { throw unsupported(); }
        @Override public ProcessingInstruction createProcessingInstruction(String target, String data) { throw unsupported(); }
        @Override public Attr createAttribute(String name) { throw unsupported(); }
        @Override public org.w3c.dom.EntityReference createEntityReference(String name) { throw unsupported(); }
        @Override public Node importNode(Node imported, boolean deep) { throw unsupported(); }
        @Override public Element createElementNS(String namespace, String name) { throw unsupported(); }
        @Override public Attr createAttributeNS(String namespace, String name) { throw unsupported(); }
        @Override public NodeList getElementsByTagNameNS(String namespace, String name) {
            return getElementsByTagName(name);
        }
        @Override public Element getElementById(String id) { return null; }
        @Override public String getInputEncoding() { return "UTF-8"; }
        @Override public String getXmlEncoding() { return "UTF-8"; }
        @Override public boolean getXmlStandalone() { return false; }
        @Override public void setXmlStandalone(boolean value) { throw unsupported(); }
        @Override public String getXmlVersion() { return "1.0"; }
        @Override public void setXmlVersion(String value) { throw unsupported(); }
        @Override public boolean getStrictErrorChecking() { return true; }
        @Override public void setStrictErrorChecking(boolean value) {}
        @Override public String getDocumentURI() { return null; }
        @Override public void setDocumentURI(String uri) {}
        @Override public Node adoptNode(Node source) { throw unsupported(); }
        @Override public org.w3c.dom.DOMConfiguration getDomConfig() { return null; }
        @Override public void normalizeDocument() {}
        @Override public Node renameNode(Node node, String namespace, String name) { throw unsupported(); }
    }

    private static final class VitaXmlElement extends VitaNode implements Element {
        final XmlReader.Element element;

        VitaXmlElement(XmlReader.Element element, VitaXmlDocument document) {
            super(element.getName(), document);
            this.element = element;
        }

        @Override public short getNodeType() { return ELEMENT_NODE; }
        @Override public NamedNodeMap getAttributes() { return new VitaXmlNamedNodeMap(element, document); }
        @Override public NodeList getChildNodes() {
            return new VitaXmlNodeList(elementChildren(element), document);
        }
        @Override public boolean hasAttributes() { return element.getAttributes().size != 0; }
        @Override public String getTextContent() { return element.getText(); }
        @Override public String getTagName() { return name; }
        @Override public String getAttribute(String name) { return element.getAttribute(name, ""); }
        @Override public boolean hasAttribute(String name) { return element.getAttributes().containsKey(name); }
        @Override public Attr getAttributeNode(String name) {
            String value = element.getAttribute(name, null);
            return value == null ? null : new VitaXmlAttribute(name, value, document);
        }
        @Override public NodeList getElementsByTagName(String name) {
            Array<XmlReader.Element> matches = new Array<>();
            for (int i = 0; i < element.getChildCount(); i++) collect(element.getChild(i), name, matches, true);
            return new VitaXmlNodeList(matches, document);
        }
        @Override public String getAttributeNS(String namespace, String name) { return getAttribute(name); }
        @Override public boolean hasAttributeNS(String namespace, String name) { return hasAttribute(name); }
        @Override public Attr getAttributeNodeNS(String namespace, String name) { return getAttributeNode(name); }
        @Override public NodeList getElementsByTagNameNS(String namespace, String name) {
            return getElementsByTagName(name);
        }
        @Override public void setAttribute(String name, String value) { throw unsupported(); }
        @Override public void removeAttribute(String name) { throw unsupported(); }
        @Override public Attr setAttributeNode(Attr attribute) { throw unsupported(); }
        @Override public Attr removeAttributeNode(Attr attribute) { throw unsupported(); }
        @Override public void setAttributeNS(String namespace, String name, String value) { throw unsupported(); }
        @Override public void removeAttributeNS(String namespace, String name) { throw unsupported(); }
        @Override public Attr setAttributeNodeNS(Attr attribute) { throw unsupported(); }
        @Override public org.w3c.dom.TypeInfo getSchemaTypeInfo() { return null; }
        @Override public void setIdAttribute(String name, boolean id) { throw unsupported(); }
        @Override public void setIdAttributeNS(String namespace, String name, boolean id) { throw unsupported(); }
        @Override public void setIdAttributeNode(Attr attribute, boolean id) { throw unsupported(); }
    }

    private static final class VitaXmlAttribute extends VitaNode implements Attr {
        final String value;

        VitaXmlAttribute(String name, String value, VitaXmlDocument document) {
            super(name, document);
            this.value = value;
        }

        @Override public short getNodeType() { return ATTRIBUTE_NODE; }
        @Override public String getNodeValue() { return value; }
        @Override public String getTextContent() { return value; }
        @Override public String getName() { return name; }
        @Override public boolean getSpecified() { return true; }
        @Override public String getValue() { return value; }
        @Override public void setValue(String value) { throw unsupported(); }
        @Override public Element getOwnerElement() { return null; }
        @Override public org.w3c.dom.TypeInfo getSchemaTypeInfo() { return null; }
        @Override public boolean isId() { return false; }
    }

    private static final class VitaXmlNamedNodeMap implements NamedNodeMap {
        final XmlReader.Element element;
        final VitaXmlDocument document;

        VitaXmlNamedNodeMap(XmlReader.Element element, VitaXmlDocument document) {
            this.element = element;
            this.document = document;
        }

        @Override public Node getNamedItem(String name) {
            String value = element.getAttribute(name, null);
            return value == null ? null : new VitaXmlAttribute(name, value, document);
        }
        @Override public int getLength() { return element.getAttributes().size; }
        @Override public Node item(int index) {
            if (index < 0 || index >= getLength()) return null;
            int current = 0;
            for (ObjectMap.Entry<String, String> entry : element.getAttributes()) {
                if (current++ == index) return new VitaXmlAttribute(entry.key, entry.value, document);
            }
            return null;
        }
        @Override public Node getNamedItemNS(String namespace, String name) { return getNamedItem(name); }
        @Override public Node setNamedItem(Node node) { throw unsupported(); }
        @Override public Node removeNamedItem(String name) { throw unsupported(); }
        @Override public Node setNamedItemNS(Node node) { throw unsupported(); }
        @Override public Node removeNamedItemNS(String namespace, String name) { throw unsupported(); }

        private DOMException unsupported() {
            return new DOMException(DOMException.NO_MODIFICATION_ALLOWED_ERR, "read-only Tiled DOM");
        }
    }

    private static final class VitaXmlNodeList implements NodeList {
        final Array<XmlReader.Element> elements;
        final VitaXmlDocument document;

        VitaXmlNodeList(Array<XmlReader.Element> elements, VitaXmlDocument document) {
            this.elements = elements;
            this.document = document;
        }

        @Override public int getLength() { return elements.size; }
        @Override public Node item(int index) {
            return index < 0 || index >= elements.size ? null : new VitaXmlElement(elements.get(index), document);
        }
    }

    private static final class EmptyNodeList implements NodeList {
        static final EmptyNodeList INSTANCE = new EmptyNodeList();
        @Override public int getLength() { return 0; }
        @Override public Node item(int index) { return null; }
    }

    private static Array<XmlReader.Element> elementChildren(XmlReader.Element parent) {
        Array<XmlReader.Element> children = new Array<>();
        for (int i = 0; i < parent.getChildCount(); i++) children.add(parent.getChild(i));
        return children;
    }

    private static void collect(XmlReader.Element element, String name,
            Array<XmlReader.Element> matches, boolean includeSelf) {
        if (element == null) return;
        if (includeSelf && ("*".equals(name) || name.equals(element.getName()))) matches.add(element);
        for (int i = 0; i < element.getChildCount(); i++) collect(element.getChild(i), name, matches, true);
    }
}
