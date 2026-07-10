import logging
import xml.etree.ElementTree as ET

logger = logging.getLogger(__name__)


def parse_health_data(xml_file, handler_fn):
    """Streams the Apple Health XML file and calls handler_fn(element, locale)
    for each direct child of the root element. Uses ElementTree.iterparse so
    the full document is never held in memory: each processed child is
    dropped from the root's children list once handled."""
    logger.info("Parsing XML file: %s", xml_file)
    context = ET.iterparse(xml_file, events=("start", "end"))
    root = None
    locale = None
    depth = 0
    for event, elem in context:
        if event == "start":
            depth += 1
            if root is None:
                root = elem
                locale = elem.attrib.get("locale")
                logger.info("Root tag: %s locale: %s", root.tag, locale)
        else:
            depth -= 1
            if depth == 1:
                handler_fn(elem, locale)
                root.remove(elem)
