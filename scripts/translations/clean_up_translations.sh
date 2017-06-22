# This script should be run after a new round of translations

# Directory where this script lives
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Find all non-default strings files
function find_files {
    find . -path "*/src/main/res/values-*" -type f -name "string*.xml"
}

# Process each file
find_files | while read file;
do
    echo "Processing: $file"

    # Remove non-translatable strings
    python $DIR/remove_non_translatable.py $file
done