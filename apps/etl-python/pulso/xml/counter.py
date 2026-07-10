import logging
import time
import xml.etree.ElementTree as ET
from collections import defaultdict

logger = logging.getLogger(__name__)


def count_elements(xml_file):
    """Counts direct children of the root element (depth 2), without
    retaining parsed subtrees."""
    logger.info("Counting elements in: %s", xml_file)
    start = time.time()
    counts = defaultdict(int)
    context = ET.iterparse(xml_file, events=("start", "end"))
    root = None
    depth = 0
    for event, elem in context:
        if event == "start":
            depth += 1
            if root is None:
                root = elem
            elif depth == 2:
                counts[elem.tag] += 1
        else:
            depth -= 1
            if depth == 1 and elem is not root:
                root.remove(elem)

    elapsed_ms = int((time.time() - start) * 1000)
    result = dict(counts)
    logger.info("Element counting complete counts=%s elapsed_ms=%s", result, elapsed_ms)
    return result
