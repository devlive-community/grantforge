interface TreeNode {
  id: number;
  title: string;
  tips: string;
  checked: boolean;
  selected: boolean;
  children?: TreeNode[];
}

export class RecursionUtils {
  public static getAllCheckedNodes (data: TreeNode[]): TreeNode[] {
    const result: TreeNode[] = []

    function getCheckedNodesHelper (nodes: TreeNode[]): void {
      for (const node of nodes) {
        if (node.checked) {
          result.push(node)
        }
        if (node.children && node.children.length > 0) {
          getCheckedNodesHelper(node.children)
        }
      }
    }

    getCheckedNodesHelper(data)
    return result
  }
}
