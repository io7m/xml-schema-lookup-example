/*
 * Copyright © 2017 Mark Raynsford <code@io7m.com> http://io7m.com
 *
 * This work is placed into the public domain for free use by anyone
 * for any purpose. It may be freely used, modified, and distributed.
 *
 * In jurisdictions that do not recognise the public domain this work
 * may be freely used, modified, and distributed without restriction.
 *
 * This work comes with absolutely no warranty.
 */

package com.io7m.xml_schema_lookup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.EntityResolver2;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class Main
{
  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  private Main()
  {

  }


  public static void main(
    final String[] args)
    throws Exception
  {
    final SAXParserFactory parsers = SAXParserFactory.newInstance();
    final SAXParser parser = parsers.newSAXParser();
    final XMLReader reader = parser.getXMLReader();

    /*
     * Turn on "secure processing". Not needed for this example, but good
     * practice in general.
     */

    reader.setFeature(
      XMLConstants.FEATURE_SECURE_PROCESSING,
      true);

    /*
     * Deny access to external schema files. We will do our own resolution
     * of schemas via a custom EntityResolver lower down in this example.
     *
     * The reason that "file" is specified here is that it's only
     * valid to pass in URL schemes that are currently understood by any
     * registered URL handlers. URLs with a "file" scheme are always going to
     * be understood by any JVM, and we override the file resolution handling
     * later on anyway (so we're not constrained to serving schemas from files).
     * It would also be possible to register a custom URLStreamHandler so that
     * we could use our own URL scheme, but this is overkill for this example.
     */

    reader.setProperty(
      XMLConstants.ACCESS_EXTERNAL_SCHEMA,
      "file");

    /*
     * Deny access to external DTD files. We don't use DTD files, so this is a
     * safe default.
     */

    reader.setProperty(
      XMLConstants.ACCESS_EXTERNAL_DTD,
      "");

    /*
     * Disable XInclude. Not used here, but safest to disable it by default.
     */

    reader.setFeature(
      "http://apache.org/xml/features/xinclude",
      false);

    /*
     * Ensure namespace processing is enabled.
     */

    reader.setFeature(
      "http://xml.org/sax/features/namespaces",
      true);

    /*
     * Tell the parser that we want validation, and more to the point, we want
     * XSD schema validation.
     */

    reader.setFeature(
      "http://xml.org/sax/features/validation",
      true);
    reader.setFeature(
      "http://apache.org/xml/features/validation/schema",
      true);

    /*
     * Create a space separated list of mappings from namespace URIs to
     * schema system IDs. This will indicate to the parser that when it encounters
     * a given namespace, it should ask the _entity resolver_ to resolve the
     * corresponding system ID that we specify here. The names such as
     * file:blue.xsd are what will be passed to our entity resolver, so that
     * we can then resolve those schemas from some application-specific location
     * (not necessarily the filesystem!).
     */

    reader.setProperty(
      "http://apache.org/xml/properties/schema/external-schemaLocation",
      "urn:com.io7m.example:blue file:blue.xsd "
        + "urn:com.io7m.example:red file:red.xsd");

    /*
     * Tell the parser to use the full EntityResolver2 interface (by default,
     * the extra EntityResolver2 methods will not be called - only those of
     * the original EntityResolver interface would be called).
     */

    reader.setFeature(
      "http://xml.org/sax/features/use-entity-resolver2",
      true);

    /*
     * Specify an entity resolver that can resolve the system IDs that we
     * specified above. Real code would almost certainly use some sort of hash
     * map structure to map systemIds to internal resources or file names. For
     * the sake of simplicity here, a switch statement is used.
     *
     * The four-argument resolveEntity method does all of the work here:
     * When one of the namespaces we specified above is encountered in a parsed file,
     * the corresponding systemId that we specified above is passed to the
     * four-argument resolveEntity method. We take this system ID and use it
     * to resolve the correct schema. Obviously, schemas don't need to be stored
     * in local files: They could come from any source that's capable of exposing
     * an InputStream (such as databases, class resources, etc). We reject
     * any systemId that we don't recognize, so this prevents hostile documents
     * from trying to get us to accept invalid data, or to open arbitrary
     * schema files.
     */

    reader.setEntityResolver(
      new EntityResolver2()
      {
        @Override
        public InputSource getExternalSubset(
          final String name,
          final String baseURI)
          throws SAXException, IOException
        {
          LOG.debug("getExternalSubset: {} {}", name, baseURI);
          throw new AssertionError("Unreachable code!");
        }

        @Override
        public InputSource resolveEntity(
          final String name,
          final String publicId,
          final String baseURI,
          final String systemId)
          throws SAXException, IOException
        {
          LOG.debug(
            "resolveEntity: {} {} {} {}",
            name,
            publicId,
            baseURI,
            systemId);

          switch (systemId) {
            case "file:blue.xsd": {
              return new InputSource(new FileInputStream(
                new File("blue.xsd")));
            }
            case "file:red.xsd": {
              return new InputSource(new FileInputStream(
                new File("red.xsd")));
            }
            default: {
              throw new IOException("Unrecognized schema: " + systemId);
            }
          }
        }

        @Override
        public InputSource resolveEntity(
          final String publicId,
          final String systemId)
          throws SAXException, IOException
        {
          LOG.debug("resolveEntity: {} {}", publicId, systemId);
          throw new AssertionError("Unreachable code!");
        }
      });

    /*
     * Set simple content handler and error handlers that will log messages
     * during parsing and count errors.
     */

    reader.setContentHandler(new LoggingContentHandler());
    final CountingErrorHandler errors = new CountingErrorHandler();
    reader.setErrorHandler(errors);

    /*
     * Parse a series of files to see how things behave with different inputs.
     */

    /*
     * A valid file using the blue schema.
     */

    try (InputStream stream =
           Files.newInputStream(Paths.get("blue_valid.xml"))) {
      reader.parse(new InputSource(stream));
    }

    if (errors.errorsCount() != 0) {
      throw new IllegalStateException("No errors were expected!");
    }

    /*
     * A valid file using the red schema.
     */

    try (InputStream stream =
           Files.newInputStream(Paths.get("red_valid.xml"))) {
      reader.parse(new InputSource(stream));
    }

    if (errors.errorsCount() != 0) {
      throw new IllegalStateException("No errors were expected!");
    }

    /*
     * An invalid file using the blue schema.
     */

    errors.errorsReset();

    try (InputStream stream =
           Files.newInputStream(Paths.get("blue_invalid.xml"))) {
      reader.parse(new InputSource(stream));
    }

    if (errors.errorsCount() == 0) {
      throw new IllegalStateException("Errors were expected but none occurred!");
    }

    /*
     * An invalid file using the red schema.
     */

    errors.errorsReset();

    try (InputStream stream =
           Files.newInputStream(Paths.get("red_invalid.xml"))) {
      reader.parse(new InputSource(stream));
    }

    if (errors.errorsCount() == 0) {
      throw new IllegalStateException("Errors were expected but none occurred!");
    }

    /*
     * A file using an unknown schema: This should be rejected.
     */

    errors.errorsReset();

    try (InputStream stream =
           Files.newInputStream(Paths.get("unknown.xml"))) {
      reader.parse(new InputSource(stream));
    }

    if (errors.errorsCount() == 0) {
      throw new IllegalStateException("Errors were expected but none occurred!");
    }

    /*
     * A file without any namespace: This should be rejected.
     */

    errors.errorsReset();

    try (InputStream stream =
           Files.newInputStream(Paths.get("no_namespace.xml"))) {
      reader.parse(new InputSource(stream));
    }

    if (errors.errorsCount() == 0) {
      throw new IllegalStateException("Errors were expected but none occurred!");
    }

    /*
     * A file that contains its own schemaLocation declaration: This should
     * be rejected; documents don't get to pick their own schema locations.
     */

    errors.errorsReset();

    try (InputStream stream =
           Files.newInputStream(Paths.get("explicit_rejected.xml"))) {
      reader.parse(new InputSource(stream));
    }

    if (errors.errorsCount() == 0) {
      throw new IllegalStateException("Errors were expected but none occurred!");
    }
  }

  private static final class LoggingContentHandler implements ContentHandler
  {
    LoggingContentHandler()
    {

    }

    @Override
    public void setDocumentLocator(
      final Locator locator)
    {
      LOG.debug("setDocumentLocator: {}", locator);
    }

    @Override
    public void startDocument()
      throws SAXException
    {
      LOG.debug("startDocument");
    }

    @Override
    public void endDocument()
      throws SAXException
    {
      LOG.debug("endDocument");
    }

    @Override
    public void startPrefixMapping(
      final String prefix,
      final String uri)
      throws SAXException
    {
      LOG.debug("startPrefixMapping: {} {}", prefix, uri);
    }

    @Override
    public void endPrefixMapping(
      final String prefix)
      throws SAXException
    {
      LOG.debug("endPrefixMapping: {}", prefix);
    }

    @Override
    public void startElement(
      final String uri,
      final String localName,
      final String qName,
      final Attributes atts)
      throws SAXException
    {
      LOG.debug("startElement: {} {} {} {}", uri, localName, qName, atts);
    }

    @Override
    public void endElement(
      final String uri,
      final String localName,
      final String qName)
      throws SAXException
    {
      LOG.debug("endElement: {} {} {}", uri, localName, qName);
    }

    @Override
    public void characters(
      final char[] ch,
      final int start,
      final int length)
      throws SAXException
    {
      LOG.debug("characters: <omitted>");
    }

    @Override
    public void ignorableWhitespace(
      final char[] ch,
      final int start,
      final int length)
      throws SAXException
    {
      LOG.debug("ignorableWhitespace: <omitted>");
    }

    @Override
    public void processingInstruction(
      final String target,
      final String data)
      throws SAXException
    {
      LOG.debug("processingInstruction: {} {}", target, data);
    }

    @Override
    public void skippedEntity(
      final String name)
      throws SAXException
    {
      LOG.debug("skippedEntity: {}", name);
    }
  }

  private static final class CountingErrorHandler implements ErrorHandler
  {
    private int errors;

    CountingErrorHandler()
    {
      this.errors = 0;
    }

    public void errorsReset()
    {
      this.errors = 0;
    }

    public int errorsCount()
    {
      return this.errors;
    }

    @Override
    public void warning(
      final SAXParseException exception)
      throws SAXException
    {
      LOG.warn("warning: ", exception);
    }

    @Override
    public void error(
      final SAXParseException exception)
      throws SAXException
    {
      LOG.warn("error: ", exception);
      ++this.errors;
    }

    @Override
    public void fatalError(
      final SAXParseException exception)
      throws SAXException
    {
      LOG.error("fatalError: ", exception);
      ++this.errors;
      throw exception;
    }
  }
}