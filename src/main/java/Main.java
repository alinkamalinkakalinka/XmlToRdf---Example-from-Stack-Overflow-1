import no.acando.xmltordf.Builder;
import no.acando.xmltordf.XmlToRdfAdvancedJena;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.RDFS;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;


public class Main {

    public static void main(String[] args) throws IOException, SAXException, ParserConfigurationException {

        XmlToRdfAdvancedJena build = Builder.getAdvancedBuilderJena()

            // set base namesapce to the foaf namespace
            .setBaseNamespace(FOAF.NS, Builder.AppliesTo.bothElementsAndAttributes)

            // <foaf:person> should be renamed to <foaf:Person> (setBaseNamespace is applied first :)
            .renameElement(FOAF.Person.toString().toLowerCase(), FOAF.Person.toString())

            // used foaf:homepage instead of website
            .renameElement(FOAF.NS + "website", FOAF.homepage.toString())

            // build an IRI for our <foaf:Person> tags (rename is applied first :)
            // IRI should be "http://www.example.com/critic/" + the contents of <foaf:name>
            .compositeId(FOAF.Person.toString()).fromElement(FOAF.name.toString()).mappedTo((elments, attributes) -> "http://www.example.com/critic/" + elments.get(FOAF.name.toString()))

            // not everything should use the foaf namespace, so rename our root xml tag to http://example.com/xml
            .renameElement(FOAF.NS + "xml", "http://example.com/xml")

            // insert foo:hasCritic instead of the default xmlToRdf:hasChild predicate between the root <xml> tag and the <foaf:Person>
            .insertPredicate("http://example.com/foo#hasCritic").betweenAnyParentAndSpecificChild(FOAF.Person.toString())
            .build();

        Model model = build
            .convertForPostProcessing(new FileInputStream("input.xml"))

            // we need to do a bit of post processing using SPARQL
            // this sparql adds foaf:homepage to person based on the "url" attribute in the <website> elements
            // it also uses the inner text of the <website> tag as the rdfs:label og the homepage
            .mustacheTransform(new ByteArrayInputStream(String.join("\n",
                "insert{",
                "   ?person <{{{foafHomepage}}}> ?urlResource.",
                "  ?urlResource <{{rdfsLabel}}> ?contact.",
                "}",
                "where{",
                "   ?a a <{{{foafHomepage}}}>;",
                "       <http://xmlns.com/foaf/0.1/url> ?url ; ",
                "       <{{{hasValue}}}> ?contact . ",

                "   ?person ?prop ?a.",
                "    BIND( IRI(CONCAT(\"http://\",?url)) as ?urlResource).",
                "}"

            ).getBytes("utf-8")), new Object() {
                String foafHomepage = FOAF.homepage.toString();
                String rdfsLabel = RDFS.label.toString();

                String hasValue = "http://acandonorway.github.com/XmlToRdf/ontology.ttl#hasValue";
            })


            // finally we can do a bit of cleanup and remove everything related to the <website> tag
            .mustacheTransform(new ByteArrayInputStream(String.join("\n",
                "delete{",
                "   ?a <{{{hasChild}}}> ?child.",
                "   ?child ?b ?c.",
                "}",
                "where{",
                "   ?a <{{{hasChild}}}> ?child.",
                "   ?child ?b ?c.",
                "}"

            ).getBytes("utf-8")), new Object() {

                String hasChild = "http://acandonorway.github.com/XmlToRdf/ontology.ttl#hasChild";

            })


            .getModel();

        // set our namespaces to make everything pretty
        model.setNsPrefix("foaf", FOAF.NS);
        model.setNsPrefix("rdfs", RDFS.getURI());
        model.setNsPrefix("foo", "http://example.com/foo#");

        // output our result to output.ttl
        model.write(new FileOutputStream("output.ttl"), "TTL");


    }

}
